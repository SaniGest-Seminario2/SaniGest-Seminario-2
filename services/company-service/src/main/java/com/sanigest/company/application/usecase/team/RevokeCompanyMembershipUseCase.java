package com.sanigest.company.application.usecase.team;

import com.sanigest.company.application.port.out.persistence.MembershipRepository;
import com.sanigest.company.application.usecase.audit.CompanyAuditRecorder;
import com.sanigest.company.domain.enums.AuditOperation;
import com.sanigest.company.domain.enums.MembershipStatus;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RevokeCompanyMembershipUseCase {
  private final CompanyMembershipPolicy policy;
  private final MembershipRepository memberships;

  private final CompanyAuditRecorder audit;

  RevokeCompanyMembershipUseCase(
      CompanyMembershipPolicy policy,
      MembershipRepository memberships,
      CompanyAuditRecorder audit) {
    this.policy = policy;
    this.memberships = memberships;
    this.audit = audit;
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void revoke(UUID actorId, UUID targetId) {
    policy.changeMembership(
        actorId,
        targetId,
        false,
        AuditOperation.MEMBER_REVOKED,
        target -> {
          policy.requireOtherOwner(target);
          memberships.revoke(target);
          audit.succeeded(
              actorId,
              target,
              AuditOperation.MEMBER_REVOKED,
              target.role(),
              null,
              target.status(),
              MembershipStatus.REVOKED);
          return null;
        });
  }
}
