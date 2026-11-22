package com.sanigest.company.application;

import com.sanigest.company.application.GetMyCompanyUseCase.MembershipRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RevokeCompanyMembershipUseCase {
  private final CompanyMembershipPolicy policy;
  private final MembershipRepository memberships;

  RevokeCompanyMembershipUseCase(CompanyMembershipPolicy policy, MembershipRepository memberships) {
    this.policy = policy;
    this.memberships = memberships;
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void revoke(UUID actorId, UUID targetId) {
    var target = policy.authorize(actorId, targetId, false);
    policy.requireOtherOwner(target);
    memberships.revoke(target);
  }
}
