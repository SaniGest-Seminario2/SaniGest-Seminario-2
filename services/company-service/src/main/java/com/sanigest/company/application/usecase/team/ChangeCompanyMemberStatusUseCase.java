package com.sanigest.company.application.usecase.team;

import com.sanigest.company.application.port.out.persistence.team.MembershipRepository;
import com.sanigest.company.application.usecase.audit.CompanyAuditRecorder;
import com.sanigest.company.domain.enums.audit.AuditOperation;
import com.sanigest.company.domain.enums.team.MembershipStatus;
import com.sanigest.company.domain.model.team.Membership;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChangeCompanyMemberStatusUseCase {
  private final CompanyMembershipPolicy policy;
  private final MembershipRepository memberships;
  private final CompanyAuditRecorder audit;

  ChangeCompanyMemberStatusUseCase(
      CompanyMembershipPolicy policy,
      MembershipRepository memberships,
      CompanyAuditRecorder audit) {
    this.policy = policy;
    this.memberships = memberships;
    this.audit = audit;
  }

  @Transactional
  public Membership change(UUID actor, UUID company, UUID targetUser, MembershipStatus status) {
    if (status != MembershipStatus.ACTIVE && status != MembershipStatus.SUSPENDED) {
      throw new IllegalArgumentException("Invalid membership status transition");
    }
    var operation =
        status == MembershipStatus.ACTIVE
            ? AuditOperation.MEMBER_ACTIVATED
            : AuditOperation.MEMBER_SUSPENDED;
    return policy.changeMembership(
        actor,
        company,
        targetUser,
        false,
        operation,
        current -> {
          if (current.status() == status) {
            return current;
          }
          if (status == MembershipStatus.SUSPENDED) {
            policy.requireOtherOwner(current);
          }
          memberships.updateStatus(current, status);
          audit.succeeded(
              actor, current, operation, current.role(), current.role(), current.status(), status);
          return new Membership(
              current.id(), current.companyId(), current.userId(), current.role(), status);
        });
  }
}
