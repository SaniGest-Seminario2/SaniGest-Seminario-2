package com.sanigest.company.application.usecase.advisor;

import com.sanigest.company.application.exception.CompanyAccessDeniedException;
import com.sanigest.company.application.exception.CompanyNotFoundException;
import com.sanigest.company.application.port.out.persistence.MembershipRepository;
import com.sanigest.company.domain.enums.CompanyRole;
import com.sanigest.company.domain.enums.MembershipStatus;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class AdvisorOwnerPolicy {
  private final MembershipRepository memberships;

  AdvisorOwnerPolicy(MembershipRepository memberships) {
    this.memberships = memberships;
  }

  UUID requireOwner(UUID actorId) {
    var membership =
        memberships.findCompanyByUserId(actorId).orElseThrow(CompanyNotFoundException::new);
    UUID companyId = membership.company().companyId();
    var actor =
        memberships
            .findByCompanyIdAndUserId(companyId, actorId)
            .orElseThrow(CompanyAccessDeniedException::new);
    if (actor.role() != CompanyRole.OWNER
        || actor.status() != MembershipStatus.ACTIVE
        || !"ACTIVE".equals(membership.company().status())) {
      throw new CompanyAccessDeniedException();
    }
    return companyId;
  }

  void lockAndRecheck(UUID actorId, UUID companyId) {
    memberships.lockMembershipChanges(companyId);
    if (!companyId.equals(requireOwner(actorId))) {
      throw new CompanyAccessDeniedException();
    }
  }
}
