package com.sanigest.company.application;

import com.sanigest.company.application.CreateCompanyUseCase.ConflictException;
import com.sanigest.company.application.GetMyCompanyUseCase.CompanyAccessDeniedException;
import com.sanigest.company.application.GetMyCompanyUseCase.MembershipRepository;
import com.sanigest.company.domain.model.Invitation;
import com.sanigest.company.domain.model.Invitation.Status;
import com.sanigest.company.domain.model.Membership;
import com.sanigest.company.domain.model.MembershipStatus;
import com.sanigest.company.domain.port.CompanyRepository;
import com.sanigest.company.domain.port.InvitationRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AcceptCompanyInvitationUseCase {
  private final InvitationRepository invitations;
  private final MembershipRepository memberships;
  private final CompanyRepository companies;
  private final Clock clock;

  public AcceptCompanyInvitationUseCase(
      InvitationRepository invitations,
      MembershipRepository memberships,
      CompanyRepository companies,
      Clock clock) {
    this.invitations = invitations;
    this.memberships = memberships;
    this.companies = companies;
    this.clock = clock;
  }

  @Transactional
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
    if (memberships.existsByUserId(userId)) {
      throw new ConflictException("User already belongs to a company");
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
    try {
      memberships.insert(membership);
    } catch (DuplicateKeyException duplicate) {
      throw new ConflictException("User already belongs to a company");
    }
    requirePending(invitation);
    invitations.closePending(invitation.id(), Status.ACCEPTED);
    return membership;
  }

  private void requirePending(Invitation invitation) {
    if (invitation.statusAt(clock.instant()) != Status.PENDING) {
      throw new ConflictException("Invitation is no longer pending");
    }
  }

  public static class InvitationNotFoundException extends RuntimeException {}
}
