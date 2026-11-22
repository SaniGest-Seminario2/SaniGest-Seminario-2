package com.sanigest.company.application;

import com.sanigest.company.application.GetMyCompanyUseCase.MembershipRepository;
import com.sanigest.company.domain.model.CompanyAuditEvent.Operation;
import com.sanigest.company.domain.model.MembershipStatus;
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
        Operation.MEMBER_REVOKED,
        target -> {
          policy.requireOtherOwner(target);
          memberships.revoke(target);
          audit.succeeded(
              actorId,
              target,
              Operation.MEMBER_REVOKED,
              target.role(),
              null,
              target.status(),
              MembershipStatus.REVOKED);
          return null;
        });
  }
}
