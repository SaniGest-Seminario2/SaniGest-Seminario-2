package com.sanigest.company.application.usecase.team;

import com.sanigest.company.application.exception.CompanyAccessDeniedException;
import com.sanigest.company.application.exception.CompanyNotFoundException;
import com.sanigest.company.application.exception.LastOwnerException;
import com.sanigest.company.application.exception.MemberNotFoundException;
import com.sanigest.company.application.port.out.persistence.MembershipRepository;
import com.sanigest.company.application.usecase.access.OperationalCompanySelection;
import com.sanigest.company.application.usecase.audit.CompanyAuditRecorder;
import com.sanigest.company.domain.enums.AuditOperation;
import com.sanigest.company.domain.enums.CompanyRole;
import com.sanigest.company.domain.enums.MembershipStatus;
import com.sanigest.company.domain.model.Membership;
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

  UUID requireAdministrator(UUID actorId, UUID requestedCompany, boolean write) {
    var company =
        OperationalCompanySelection.profile(memberships, actorId, requestedCompany)
            .orElseThrow(CompanyNotFoundException::new)
            .company();
    if (write) {
      memberships.lockMembershipChanges(company.companyId());
    }
    requireAdministrator(actorId, company.companyId(), company.status());
    return company.companyId();
  }

  <T> T changeMembership(
      UUID actorId,
      UUID requestedCompany,
      UUID targetId,
      boolean assigningOwner,
      AuditOperation operation,
      Function<Membership, T> change) {
    var scope =
        OperationalCompanySelection.profile(memberships, actorId, requestedCompany)
            .orElseThrow(CompanyNotFoundException::new);
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

    var actor = requireAdministrator(actorId, companyId, companyStatus);
    if (assigningOwner && actor.role() != CompanyRole.OWNER) {
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

  private Membership requireAdministrator(UUID actorId, UUID companyId, String companyStatus) {
    var actor =
        memberships
            .findByCompanyIdAndUserId(companyId, actorId)
            .orElseThrow(CompanyAccessDeniedException::new);
    if (actor.status() != MembershipStatus.ACTIVE
        || (actor.role() != CompanyRole.OWNER && actor.role() != CompanyRole.ADMIN)
        || !"ACTIVE".equals(companyStatus)) {
      throw new CompanyAccessDeniedException();
    }

    return actor;
  }

  void requireOtherOwner(Membership target) {
    if (target.role() == CompanyRole.OWNER
        && target.status() == MembershipStatus.ACTIVE
        && !memberships.hasOtherActiveOwner(target.companyId(), target.userId())) {
      throw new LastOwnerException();
    }
  }
}
