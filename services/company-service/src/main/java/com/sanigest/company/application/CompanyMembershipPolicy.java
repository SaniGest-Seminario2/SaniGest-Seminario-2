package com.sanigest.company.application;

import com.sanigest.company.application.GetMyCompanyUseCase.CompanyAccessDeniedException;
import com.sanigest.company.application.GetMyCompanyUseCase.CompanyNotFoundException;
import com.sanigest.company.application.GetMyCompanyUseCase.MembershipRepository;
import com.sanigest.company.domain.model.CompanyAuditEvent.Operation;
import com.sanigest.company.domain.model.CompanyRole;
import com.sanigest.company.domain.model.Membership;
import com.sanigest.company.domain.model.MembershipStatus;
import java.util.UUID;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CompanyMembershipPolicy {
  private static final Logger LOG = LoggerFactory.getLogger(CompanyMembershipPolicy.class);
  private final MembershipRepository memberships;
  private final CompanyAuditRecorder audit;

  CompanyMembershipPolicy(MembershipRepository memberships, CompanyAuditRecorder audit) {
    this.memberships = memberships;
    this.audit = audit;
  }

  <T> T changeMembership(
      UUID actorId,
      UUID targetId,
      boolean assigningOwner,
      Operation operation,
      Function<Membership, T> change) {
    var scope = memberships.findCompanyByUserId(actorId).orElseThrow(CompanyNotFoundException::new);
    UUID companyId = scope.company().companyId();
    memberships.lockMembershipChanges(companyId);
    Membership target = null;
    try {
      target = authorize(companyId, actorId, targetId, assigningOwner, scope.company().status());
      return change.apply(target);
    } catch (CompanyAccessDeniedException | MemberNotFoundException | LastOwnerException rejected) {
      // Record only where the actor has a trusted tenant. Never persist an unresolved target ID.
      if (scope.status() == MembershipStatus.ACTIVE) {
        try {
          audit.rejected(companyId, actorId, target == null ? null : target.userId(), operation);
        } catch (RuntimeException auditFailure) {
          LOG.error("Could not persist rejected company membership audit event");
        }
      }
      throw rejected;
    }
  }

  private Membership authorize(
      UUID companyId, UUID actorId, UUID targetId, boolean assigningOwner, String companyStatus) {

    // Read the actor after acquiring the lock so a queued request sees current permissions.
    var actor =
        memberships
            .findByCompanyIdAndUserId(companyId, actorId)
            .orElseThrow(CompanyAccessDeniedException::new);
    if (actor.status() != MembershipStatus.ACTIVE
        || (actor.role() != CompanyRole.OWNER && actor.role() != CompanyRole.ADMIN)
        || (assigningOwner && actor.role() != CompanyRole.OWNER)
        || !"ACTIVE".equals(companyStatus)) {
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
