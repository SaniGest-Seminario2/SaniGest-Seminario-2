package com.sanigest.company.application.usecase.team;

import com.sanigest.company.application.dto.CreatedInvitation;
import com.sanigest.company.application.exception.CompanyConflictException;
import com.sanigest.company.application.port.out.integration.InvitationDelivery;
import com.sanigest.company.application.port.out.persistence.InvitationRepository;
import com.sanigest.company.application.usecase.audit.CompanyAuditRecorder;
import com.sanigest.company.domain.enums.AuditOperation;
import com.sanigest.company.domain.enums.CompanyRole;
import com.sanigest.company.domain.enums.InvitationStatus;
import com.sanigest.company.domain.model.Invitation;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class InviteCompanyUserUseCase {
  private static final Logger LOGGER = LoggerFactory.getLogger(InviteCompanyUserUseCase.class);

  private final CompanyMembershipPolicy policy;
  private final InvitationRepository invitations;
  private final Duration ttl;
  private final Clock clock;
  private final Optional<InvitationDelivery> delivery;
  private final CompanyAuditRecorder audit;

  public InviteCompanyUserUseCase(
      CompanyMembershipPolicy policy,
      InvitationRepository invitations,
      @Value("${sanigest.invitations.ttl:PT72H}") Duration ttl,
      Clock clock,
      Optional<InvitationDelivery> delivery,
      CompanyAuditRecorder audit) {
    if (ttl.compareTo(Duration.ofSeconds(1)) < 0) {
      throw new IllegalArgumentException("Invitation TTL must be at least one second");
    }
    this.policy = policy;
    this.invitations = invitations;
    this.ttl = ttl;
    this.clock = clock;
    this.delivery = delivery;
    this.audit = audit;
  }

  @Transactional
  public CreatedInvitation invite(UUID actor, String email, CompanyRole role, boolean replace) {
    return invite(actor, null, email, role, replace);
  }

  @Transactional
  public CreatedInvitation invite(
      UUID actor, UUID requestedCompany, String email, CompanyRole role, boolean replace) {
    return invite(actor, requestedCompany, email, role, replace, null);
  }

  @Transactional
  public CreatedInvitation invite(
      UUID actor,
      UUID requestedCompany,
      String email,
      CompanyRole role,
      boolean replace,
      String name) {
    UUID companyId = policy.requireAdministrator(actor, requestedCompany, true);
    String recipient = Invitation.normalizeEmail(email);
    var prior = invitations.findPendingForUpdate(companyId, recipient);
    Instant now = clock.instant();
    var invitation =
        new Invitation(
            UUID.randomUUID(),
            companyId,
            recipient,
            role,
            InvitationStatus.PENDING,
            now,
            now.plus(ttl),
            name);
    prior.ifPresent(existing -> {
      if (existing.statusAt(now) == InvitationStatus.EXPIRED) {
        invitations.closePending(existing.id(), InvitationStatus.EXPIRED);
      } else if (replace) {
        invitations.closePending(existing.id(), InvitationStatus.REVOKED);
      } else {
        throw new CompanyConflictException("An invitation is already pending");
      }
    });
    String token = InvitationToken.generate();
    invitations.insert(invitation, InvitationToken.hash(token));
    boolean replaced =
        prior.filter(existing -> existing.statusAt(now) == InvitationStatus.PENDING).isPresent();
    audit.invitationChanged(
        companyId,
        actor,
        replaced ? AuditOperation.INVITATION_REPLACED : AuditOperation.INVITATION_CREATED);
    delivery.ifPresent(adapter ->
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
              @Override
              public void afterCommit() {
                try {
                  adapter.deliver(recipient, token, invitation.expiresAt());
                } catch (RuntimeException failure) {
                  LOGGER.warn("Invitation delivery failed");
                }
              }
            }));
    return new CreatedInvitation(
        invitation.id(),
        role,
        InvitationStatus.PENDING,
        invitation.expiresAt(),
        invitation.name(),
        recipient);
  }
}
