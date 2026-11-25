package com.sanigest.company.application.usecase.team;

import com.sanigest.company.application.exception.CompanyAccessDeniedException;
import com.sanigest.company.application.exception.CompanyConflictException;
import com.sanigest.company.application.exception.InvitationNotFoundException;
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
import java.util.UUID;
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

  public AcceptCompanyInvitationUseCase(
      InvitationRepository invitations,
      MembershipRepository memberships,
      CompanyRepository companies,
      Clock clock,
      CompanyAuditRecorder audit) {
    this.invitations = invitations;
    this.memberships = memberships;
    this.companies = companies;
    this.clock = clock;
    this.audit = audit;
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Membership accept(UUID userId, String verifiedEmail, String token) {
    if (verifiedEmail == null) {
      throw new CompanyAccessDeniedException();
    }
    var invitation =
        invitations
            .findByTokenHashForUpdate(InvitationToken.hash(token))
            .orElseThrow(InvitationNotFoundException::new);
    if (!invitation.isFor(verifiedEmail)) {
      throw new CompanyAccessDeniedException();
    }
    requirePending(invitation);
    memberships.lockMembershipCreation(userId);
    if (memberships.existsByUserId(userId)) {
      throw new CompanyConflictException("User already belongs to a company");
    }
    var company =
        companies
            .findByIdForUpdate(invitation.companyId())
            .orElseThrow(InvitationNotFoundException::new);
    if (!"ACTIVE".equals(company.status())) {
      throw new CompanyAccessDeniedException();
    }
    requirePending(invitation);
    var membership =
        new Membership(
            UUID.randomUUID(), company.id(), userId, invitation.role(), MembershipStatus.ACTIVE);
    memberships.insert(membership);
    requirePending(invitation);
    invitations.closePending(invitation.id(), InvitationStatus.ACCEPTED);
    audit.succeeded(
        userId,
        membership,
        AuditOperation.MEMBER_ACTIVATED,
        null,
        membership.role(),
        null,
        MembershipStatus.ACTIVE);
    return membership;
  }

  private void requirePending(Invitation invitation) {
    if (invitation.statusAt(clock.instant()) != InvitationStatus.PENDING) {
      throw new CompanyConflictException("Invitation is no longer pending");
    }
  }
}
