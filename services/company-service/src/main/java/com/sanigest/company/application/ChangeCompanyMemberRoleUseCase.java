package com.sanigest.company.application;

import com.sanigest.company.application.CompanyMembershipPolicy.MemberNotFoundException;
import com.sanigest.company.application.GetMyCompanyUseCase.MembershipRepository;
import com.sanigest.company.domain.model.CompanyAuditEvent.Operation;
import com.sanigest.company.domain.model.CompanyRole;
import com.sanigest.company.domain.model.Membership;
import com.sanigest.company.domain.model.MembershipStatus;
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
    if (role == null) {
      throw new IllegalArgumentException("A company role is required");
    }
    return policy.changeMembership(
        actorId,
        targetId,
        role == CompanyRole.OWNER,
        Operation.ROLE_CHANGED,
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
    audit.succeeded(actorId, target, Operation.ROLE_CHANGED, target.role(), role, null, null);
    return new Membership(target.id(), target.companyId(), target.userId(), role, target.status());
  }
}
