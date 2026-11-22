package com.sanigest.company.application;

import com.sanigest.company.application.CreateCompanyUseCase.ConflictException;
import com.sanigest.company.application.GetMyCompanyUseCase.CompanyAccessDeniedException;
import com.sanigest.company.application.GetMyCompanyUseCase.CompanyNotFoundException;
import com.sanigest.company.application.GetMyCompanyUseCase.MembershipRepository;
import com.sanigest.company.domain.model.CompanyRole;
import com.sanigest.company.domain.model.Invitation;
import com.sanigest.company.domain.model.Invitation.Status;
import com.sanigest.company.domain.model.MembershipStatus;
import com.sanigest.company.domain.port.CompanyRepository;
import com.sanigest.company.domain.port.InvitationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
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
    UUID companyId = authorizedCompany(actor);
    String recipient = Invitation.normalizeEmail(email);
    var prior = invitations.findPendingForUpdate(companyId, recipient);
    Instant now = clock.instant();
    var invitation =
        new Invitation(
            UUID.randomUUID(), companyId, recipient, role, Status.PENDING, now, now.plus(ttl));
    prior.ifPresent(existing -> {
      if (existing.statusAt(now) == Status.EXPIRED) {
        invitations.closePending(existing.id(), Status.EXPIRED);
      } else if (replace) {
        invitations.closePending(existing.id(), Status.REVOKED);
      } else {
        throw new ConflictException("An invitation is already pending");
      }
    });
    String token = InvitationToken.generate();
    try {
      invitations.insert(invitation, InvitationToken.hash(token));
    } catch (DuplicateKeyException duplicate) {
      throw new ConflictException("An invitation is already pending");
    }
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
    return new CreatedInvitation(invitation.id(), role, Status.PENDING, invitation.expiresAt());
  }

  private UUID authorizedCompany(UUID actor) {
    var membership =
        memberships.findByUserIdForUpdate(actor).orElseThrow(CompanyNotFoundException::new);
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

  public record CreatedInvitation(
      UUID invitationId, CompanyRole role, Status status, Instant expiresAt) {}
}
