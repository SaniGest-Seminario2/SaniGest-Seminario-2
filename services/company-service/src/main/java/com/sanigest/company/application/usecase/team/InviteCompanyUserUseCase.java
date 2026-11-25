package com.sanigest.company.application.usecase.team;

import com.sanigest.company.application.dto.CreatedInvitation;
import com.sanigest.company.application.exception.CompanyAccessDeniedException;
import com.sanigest.company.application.exception.CompanyConflictException;
import com.sanigest.company.application.exception.CompanyNotFoundException;
import com.sanigest.company.application.port.out.integration.InvitationDelivery;
import com.sanigest.company.application.port.out.persistence.CompanyRepository;
import com.sanigest.company.application.port.out.persistence.InvitationRepository;
import com.sanigest.company.application.port.out.persistence.MembershipRepository;
import com.sanigest.company.application.usecase.access.OperationalCompanySelection;
import com.sanigest.company.domain.enums.CompanyRole;
import com.sanigest.company.domain.enums.InvitationStatus;
import com.sanigest.company.domain.enums.MembershipStatus;
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

  private final MembershipRepository memberships;
  private final CompanyRepository companies;
  private final InvitationRepository invitations;
  private final Duration ttl;
  private final Clock clock;
  private final Optional<InvitationDelivery> delivery;

  public InviteCompanyUserUseCase(
      MembershipRepository memberships,
      CompanyRepository companies,
      InvitationRepository invitations,
      @Value("${sanigest.invitations.ttl:PT72H}") Duration ttl,
      Clock clock,
      Optional<InvitationDelivery> delivery) {
    if (ttl.compareTo(Duration.ofSeconds(1)) < 0) {
      throw new IllegalArgumentException("Invitation TTL must be at least one second");
    }
    this.memberships = memberships;
    this.companies = companies;
    this.invitations = invitations;
    this.ttl = ttl;
    this.clock = clock;
    this.delivery = delivery;
  }

  @Transactional
  public CreatedInvitation invite(UUID actor, String email, CompanyRole role, boolean replace) {
    return invite(actor, null, email, role, replace);
  }

  @Transactional
  public CreatedInvitation invite(
      UUID actor, UUID requestedCompany, String email, CompanyRole role, boolean replace) {
    UUID companyId = authorizedCompany(actor, requestedCompany);
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
            now.plus(ttl));
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
        invitation.id(), role, InvitationStatus.PENDING, invitation.expiresAt());
  }

  private UUID authorizedCompany(UUID actor, UUID requestedCompany) {
    var membership =
        OperationalCompanySelection.lockMembership(memberships, actor, requestedCompany)
            .orElseThrow(CompanyNotFoundException::new);
    if (membership.status() != MembershipStatus.ACTIVE
        || (membership.role() != CompanyRole.OWNER && membership.role() != CompanyRole.ADMIN)) {
      throw new CompanyAccessDeniedException();
    }
    var company =
        companies.findById(membership.companyId()).orElseThrow(CompanyNotFoundException::new);
    if (!"ACTIVE".equals(company.status())) {
      throw new CompanyAccessDeniedException();
    }
    return company.id();
  }
}
