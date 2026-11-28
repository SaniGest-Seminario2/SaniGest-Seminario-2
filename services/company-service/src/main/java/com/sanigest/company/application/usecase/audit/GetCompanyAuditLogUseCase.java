package com.sanigest.company.application.usecase.audit;

import static com.sanigest.company.application.usecase.access.ResolveCompanyAccess.AUDIT_LOG_READ;

import com.sanigest.company.application.dto.audit.AuditPage;
import com.sanigest.company.application.exception.access.CompanyAccessDeniedException;
import com.sanigest.company.application.port.out.persistence.audit.CompanyAuditRepository;
import com.sanigest.company.application.port.out.persistence.team.MembershipRepository;
import com.sanigest.company.application.usecase.access.OperationalCompanySelection;
import com.sanigest.company.domain.enums.team.CompanyRole;
import com.sanigest.company.domain.enums.team.MembershipStatus;
import com.sanigest.web.application.dto.audit.AuditPage.Filters;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetCompanyAuditLogUseCase {
  private final MembershipRepository memberships;
  private final CompanyAuditRepository audit;

  GetCompanyAuditLogUseCase(MembershipRepository memberships, CompanyAuditRepository audit) {
    this.memberships = memberships;
    this.audit = audit;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public AuditPage get(UUID actorId, Instant from, Instant to, int page, int size) {
    return get(actorId, null, from, to, page, size);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public AuditPage get(
      UUID actorId, UUID requestedCompany, Instant from, Instant to, int page, int size) {
    return get(
        actorId, requestedCompany, new Filters(from, to, null, null, null, null, page, size));
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public AuditPage get(UUID actorId, UUID requestedCompany, Filters filters) {
    int page = filters.page();
    int size = filters.size();

    var company =
        OperationalCompanySelection.profile(memberships, actorId, requestedCompany)
            .filter(entry -> entry.status() == MembershipStatus.ACTIVE)
            .map(entry -> entry.company())
            .filter(profile -> "ACTIVE".equals(profile.status()))
            .orElseThrow(CompanyAccessDeniedException::new);
    var actor =
        memberships
            .findByCompanyIdAndUserId(company.companyId(), actorId)
            .filter(member -> member.status() == MembershipStatus.ACTIVE)
            .orElseThrow(CompanyAccessDeniedException::new);
    if (actor.role() != CompanyRole.OWNER && !memberships.hasPermission(actor, AUDIT_LOG_READ)) {
      throw new CompanyAccessDeniedException();
    }

    var rows = audit.findPage(company.companyId(), filters);
    boolean hasNext = rows.size() > size;
    return new AuditPage(
        List.copyOf(rows.subList(0, Math.min(size, rows.size()))), page, size, hasNext);
  }
}
