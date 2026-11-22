package com.sanigest.company.application;

import com.sanigest.company.application.GetMyCompanyUseCase.CompanyAccessDeniedException;
import com.sanigest.company.application.GetMyCompanyUseCase.CompanyNotFoundException;
import com.sanigest.company.application.GetMyCompanyUseCase.MembershipRepository;
import com.sanigest.company.domain.model.CompanyRole;
import com.sanigest.company.domain.model.Membership;
import com.sanigest.company.domain.model.MembershipStatus;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CompanyMembershipPolicy {
  private final MembershipRepository memberships;

  CompanyMembershipPolicy(MembershipRepository memberships) {
    this.memberships = memberships;
  }

  Membership authorize(UUID actorId, UUID targetId, boolean assigningOwner) {
    var company =
        memberships
            .findCompanyByUserId(actorId)
            .orElseThrow(CompanyNotFoundException::new)
            .company();
    UUID companyId = company.companyId();
    memberships.lockMembershipChanges(companyId);

    // Read the actor after acquiring the lock so a queued request sees current permissions.
    var actor =
        memberships
            .findByCompanyIdAndUserId(companyId, actorId)
            .orElseThrow(CompanyAccessDeniedException::new);
    if (actor.status() != MembershipStatus.ACTIVE
        || (actor.role() != CompanyRole.OWNER && actor.role() != CompanyRole.ADMIN)
        || (assigningOwner && actor.role() != CompanyRole.OWNER)
        || !"ACTIVE".equals(company.status())) {
      throw new CompanyAccessDeniedException();
    }

    var target =
        memberships
            .findByCompanyIdAndUserId(companyId, targetId)
            .filter(member -> member.status() != MembershipStatus.REVOKED)
            .orElseThrow(MemberNotFoundException::new);
    if (actor.role() != CompanyRole.OWNER && target.role() == CompanyRole.OWNER) {
      throw new CompanyAccessDeniedException();
    }
    return target;
  }

  void requireOtherOwner(Membership target) {
    if (target.role() == CompanyRole.OWNER
        && target.status() == MembershipStatus.ACTIVE
        && !memberships.hasOtherActiveOwner(target.companyId(), target.userId())) {
      throw new LastOwnerException();
    }
  }

  public static class MemberNotFoundException extends RuntimeException {}

  public static class LastOwnerException extends RuntimeException {}
}
