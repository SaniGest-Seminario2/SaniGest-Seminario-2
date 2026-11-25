package com.sanigest.company.application.usecase.team;

import com.sanigest.company.application.exception.MemberNotFoundException;
import com.sanigest.company.application.port.out.persistence.MembershipRepository;
import com.sanigest.company.application.usecase.audit.CompanyAuditRecorder;
import com.sanigest.company.domain.enums.AuditOperation;
import com.sanigest.company.domain.enums.CompanyRole;
import com.sanigest.company.domain.enums.MembershipStatus;
import com.sanigest.company.domain.model.Membership;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChangeCompanyMemberRoleUseCase {
  private final CompanyMembershipPolicy policy;
  private final MembershipRepository memberships;

  private final CompanyAuditRecorder audit;

  ChangeCompanyMemberRoleUseCase(
      CompanyMembershipPolicy policy,
      MembershipRepository memberships,
      CompanyAuditRecorder audit) {
    this.policy = policy;
    this.memberships = memberships;
    this.audit = audit;
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Membership change(UUID actorId, UUID targetId, CompanyRole role) {
    return change(actorId, null, targetId, role);
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Membership change(UUID actorId, UUID requestedCompany, UUID targetId, CompanyRole role) {
    if (role == null) {
      throw new IllegalArgumentException("A company role is required");
    }
    return policy.changeMembership(
        actorId,
        requestedCompany,
        targetId,
        role == CompanyRole.OWNER,
        AuditOperation.ROLE_CHANGED,
        target -> changeRole(actorId, target, role));
  }

  private Membership changeRole(UUID actorId, Membership target, CompanyRole role) {
    if (target.status() != MembershipStatus.ACTIVE) {
      throw new MemberNotFoundException();
    }
    if (target.role() == role) {
      return target;
    }
    policy.requireOtherOwner(target);
    memberships.updateRole(target, role);
    audit.succeeded(actorId, target, AuditOperation.ROLE_CHANGED, target.role(), role, null, null);
    return new Membership(target.id(), target.companyId(), target.userId(), role, target.status());
  }
}
