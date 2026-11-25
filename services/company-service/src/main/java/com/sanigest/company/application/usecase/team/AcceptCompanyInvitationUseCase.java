package com.sanigest.company.application.usecase.team;

import com.sanigest.company.application.exception.CompanyAccessDeniedException;
import com.sanigest.company.application.exception.CompanyConflictException;
import com.sanigest.company.application.exception.InvitationNotFoundException;
import com.sanigest.company.application.port.out.integration.MemberIdentityDirectory;
import com.sanigest.company.application.port.out.persistence.CompanyRepository;
import com.sanigest.company.application.port.out.persistence.InvitationRepository;
import com.sanigest.company.application.port.out.persistence.MembershipRepository;
import com.sanigest.company.application.usecase.audit.CompanyAuditRecorder;
import com.sanigest.company.domain.enums.AuditOperation;
import com.sanigest.company.domain.enums.InvitationStatus;
import com.sanigest.company.domain.enums.MembershipStatus;
import com.sanigest.company.domain.model.Invitation;
import com.sanigest.company.domain.model.Membership;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AcceptCompanyInvitationUseCase {
  private final InvitationRepository invitations;
  private final MembershipRepository memberships;
  private final CompanyRepository companies;
  private final Clock clock;
  private final CompanyAuditRecorder audit;
  private final boolean multipleMembershipsEnabled;
  private final MemberIdentityDirectory directory;

  public AcceptCompanyInvitationUseCase(
      InvitationRepository invitations,
      MembershipRepository memberships,
      CompanyRepository companies,
      Clock clock,
      CompanyAuditRecorder audit,
      MemberIdentityDirectory directory,
      @Value("${sanigest.company.multiple-memberships-enabled:false}")
          boolean multipleMembershipsEnabled) {
    this.invitations = invitations;
    this.memberships = memberships;
    this.companies = companies;
    this.clock = clock;
    this.audit = audit;
    this.directory = directory;
    this.multipleMembershipsEnabled = multipleMembershipsEnabled;
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Membership accept(UUID userId, String token) {
    String hash = InvitationToken.hash(token);
    var identity = directory.lookup(List.of(userId)).get(userId);
    if (identity == null || !identity.emailVerified()) {
      throw new CompanyAccessDeniedException();
    }
    return acceptVerified(userId, identity.email(), hash);
  }

  private Membership acceptVerified(UUID userId, String verifiedEmail, String hash) {
    UUID companyId =
        invitations.findCompanyByTokenHash(hash).orElseThrow(InvitationNotFoundException::new);
    memberships.lockMembershipCreation(userId);
    memberships.lockMembershipChanges(companyId);
    var invitation =
        invitations.findByTokenHashForUpdate(hash).orElseThrow(InvitationNotFoundException::new);
    if (!invitation.isFor(verifiedEmail)) {
      throw new CompanyAccessDeniedException();
    }
    requirePending(invitation);
    var previous = priorMembership(invitation, userId);
    var company = companies.findById(companyId).orElseThrow(InvitationNotFoundException::new);
    if (!"ACTIVE".equals(company.status())) {
      throw new CompanyAccessDeniedException();
    }
    return activate(userId, invitation, previous);
  }

  private Membership priorMembership(Invitation invitation, UUID userId) {
    UUID companyId = invitation.companyId();
    var previous = memberships.findByCompanyIdAndUserId(companyId, userId).orElse(null);
    if (previous != null
        && (previous.status() != MembershipStatus.REVOKED
            || !memberships.revokedBefore(companyId, userId, invitation.createdAt()))) {
      throw new CompanyConflictException("A membership already exists for this company");
    }
    if (!multipleMembershipsEnabled
        && memberships.existsByUserIdOutsideCompany(userId, companyId)) {
      throw new CompanyConflictException("User already belongs to a company");
    }
    return previous;
  }

  private Membership activate(UUID userId, Invitation invitation, Membership previous) {
    requirePending(invitation);
    var membership =
        new Membership(
            previous == null ? UUID.randomUUID() : previous.id(),
            invitation.companyId(),
            userId,
            invitation.role(),
            MembershipStatus.ACTIVE);
    if (previous == null) {
      memberships.insert(membership);
    } else {
      memberships.reinstate(membership);
    }
    requirePending(invitation);
    invitations.closePending(invitation.id(), InvitationStatus.ACCEPTED);
    audit.succeeded(
        userId,
        membership,
        AuditOperation.MEMBER_ACTIVATED,
        previous == null ? null : previous.role(),
        membership.role(),
        previous == null ? null : previous.status(),
        MembershipStatus.ACTIVE);
    return membership;
  }

  private void requirePending(Invitation invitation) {
    if (invitation.statusAt(clock.instant()) != InvitationStatus.PENDING) {
      throw new CompanyConflictException("Invitation is no longer pending");
    }
  }
}
