package com.sanigest.company.application;

import com.sanigest.company.application.GetMyCompanyUseCase.CompanyAccessDeniedException;
import com.sanigest.company.application.GetMyCompanyUseCase.MembershipRepository;
import com.sanigest.company.domain.model.CompanyAuditEvent;
import com.sanigest.company.domain.model.CompanyRole;
import com.sanigest.company.domain.model.MembershipStatus;
import com.sanigest.company.domain.port.CompanyAuditRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetCompanyAuditLogUseCase {
  static final String AUDIT_LOG_READ = "AUDIT_LOG_READ";

  private final MembershipRepository memberships;
  private final CompanyAuditRepository audit;

  GetCompanyAuditLogUseCase(MembershipRepository memberships, CompanyAuditRepository audit) {
    this.memberships = memberships;
    this.audit = audit;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public AuditPage get(UUID actorId, Instant from, Instant to, int page, int size) {
    if (page < 0 || size < 1 || size > 100 || (from != null && to != null && from.isAfter(to))) {
      throw new IllegalArgumentException("Invalid audit log filters");
    }

    var company =
        memberships
            .findCompanyByUserId(actorId)
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

    var rows = audit.findPage(company.companyId(), from, to, (long) page * size, size + 1);
    boolean hasNext = rows.size() > size;
    return new AuditPage(
        List.copyOf(rows.subList(0, Math.min(size, rows.size()))), page, size, hasNext);
  }

  public record AuditPage(List<CompanyAuditEvent> content, int page, int size, boolean hasNext) {}
}
