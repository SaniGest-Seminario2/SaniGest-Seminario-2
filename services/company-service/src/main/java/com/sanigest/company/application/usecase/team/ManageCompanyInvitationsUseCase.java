package com.sanigest.company.application.usecase.team;

import com.sanigest.company.application.exception.CompanyConflictException;
import com.sanigest.company.application.exception.InvitationNotFoundException;
import com.sanigest.company.application.port.out.persistence.InvitationRepository;
import com.sanigest.company.application.usecase.audit.CompanyAuditRecorder;
import com.sanigest.company.domain.enums.AuditOperation;
import com.sanigest.company.domain.enums.InvitationStatus;
import com.sanigest.company.domain.model.Invitation;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManageCompanyInvitationsUseCase {
  private final CompanyMembershipPolicy policy;
  private final InvitationRepository invitations;
  private final CompanyAuditRecorder audit;
  private final Clock clock;

  ManageCompanyInvitationsUseCase(
      CompanyMembershipPolicy policy,
      InvitationRepository invitations,
      CompanyAuditRecorder audit,
      Clock clock) {
    this.policy = policy;
    this.invitations = invitations;
    this.audit = audit;
    this.clock = clock;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public List<Invitation> pending(UUID actor, UUID requestedCompany, int page, int size) {
    if (page < 0 || size < 1 || size > 100) {
      throw new IllegalArgumentException("Invalid invitation page parameters");
    }
    var company = policy.requireAdministrator(actor, requestedCompany, false);
    return invitations.findPending(company, clock.instant(), size, (long) page * size);
  }

  @Transactional
  public void revoke(UUID actor, UUID requestedCompany, UUID id) {
    var company = policy.requireAdministrator(actor, requestedCompany, true);
    var invitation =
        invitations
            .findByCompanyAndIdForUpdate(company, id)
            .orElseThrow(InvitationNotFoundException::new);
    if (invitation.statusAt(clock.instant()) != InvitationStatus.PENDING) {
      throw new CompanyConflictException("Invitation is no longer pending");
    }
    invitations.closePending(id, InvitationStatus.REVOKED);
    audit.invitationChanged(company, actor, id, AuditOperation.INVITATION_REVOKED);
  }
}
