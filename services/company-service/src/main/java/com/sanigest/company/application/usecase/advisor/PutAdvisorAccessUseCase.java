package com.sanigest.company.application.usecase.advisor;

import com.sanigest.company.application.exception.access.CompanyAccessDeniedException;
import com.sanigest.company.application.exception.profile.CompanyConflictException;
import com.sanigest.company.application.port.out.integration.advisor.AdvisorEligibility;
import com.sanigest.company.application.port.out.persistence.advisor.AdvisorAccessRepository;
import com.sanigest.company.application.usecase.audit.CompanyAuditRecorder;
import com.sanigest.company.domain.enums.audit.AuditOperation;
import com.sanigest.company.domain.model.advisor.AdvisorAccess;
import com.sanigest.company.domain.model.advisor.AdvisorPermission;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PutAdvisorAccessUseCase {
  private static final Logger LOG = LoggerFactory.getLogger(PutAdvisorAccessUseCase.class);
  private final AdvisorOwnerPolicy owners;
  private final CompanyAuditRecorder audit;
  private final AdvisorEligibility eligibility;
  private final AdvisorAccessRepository access;
  private final AdvisorAccessChanges changes;
  private final TransactionTemplate transactions;

  PutAdvisorAccessUseCase(
      AdvisorOwnerPolicy owners,
      CompanyAuditRecorder audit,
      AdvisorEligibility eligibility,
      AdvisorAccessRepository access,
      AdvisorAccessChanges changes,
      TransactionTemplate transactions) {
    this.owners = owners;
    this.audit = audit;
    this.eligibility = eligibility;
    this.access = access;
    this.changes = changes;
    this.transactions = transactions;
  }

  public AdvisorAccess put(
      UUID actorId, UUID advisorId, long revision, List<AdvisorPermission> permissions) {
    return put(actorId, null, advisorId, revision, permissions);
  }

  public AdvisorAccess put(
      UUID actorId,
      UUID requestedCompany,
      UUID advisorId,
      long revision,
      List<AdvisorPermission> permissions) {
    if (revision < 0
        || permissions.isEmpty()
        || permissions.stream().distinct().count() != permissions.size()) {
      throw new IllegalArgumentException("Invalid advisor permissions or revision");
    }
    UUID companyId = owners.requireOwner(actorId, requestedCompany);
    if (actorId.equals(advisorId) || !eligibility.isEligible(advisorId)) {
      try {
        audit.rejected(companyId, actorId, null, AuditOperation.ADVISOR_GRANTED);
      } catch (RuntimeException auditFailure) {
        LOG.error("Could not persist rejected advisor grant audit event");
      }
      throw new CompanyAccessDeniedException();
    }
    var requested =
        permissions.stream()
            .sorted(
                Comparator.comparing((AdvisorPermission p) -> p.module().name())
                    .thenComparing(p -> p.scope().name()))
            .toList();
    return transactions.execute(status -> {
      owners.lockAndRecheck(actorId, companyId);
      var current = access.find(companyId, advisorId);
      if (current.permissions().equals(requested)) {
        return current;
      }
      if (current.revision() != revision) {
        throw new CompanyConflictException("Advisor access has changed");
      }
      return changes.replace(companyId, actorId, current, requested);
    });
  }
}
