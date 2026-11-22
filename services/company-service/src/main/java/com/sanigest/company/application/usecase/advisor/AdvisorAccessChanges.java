package com.sanigest.company.application.usecase.advisor;

import com.sanigest.company.application.port.out.persistence.AdvisorAccessRepository;
import com.sanigest.company.application.usecase.audit.CompanyAuditRecorder;
import com.sanigest.company.domain.enums.AuditOperation;
import com.sanigest.company.domain.model.AdvisorAccess;
import com.sanigest.company.domain.model.AdvisorPermission;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class AdvisorAccessChanges {
  private final AdvisorAccessRepository access;
  private final CompanyAuditRecorder audit;
  private final Clock clock;

  AdvisorAccessChanges(AdvisorAccessRepository access, CompanyAuditRecorder audit, Clock clock) {
    this.access = access;
    this.audit = audit;
    this.clock = clock;
  }

  AdvisorAccess replace(
      UUID companyId, UUID actorId, AdvisorAccess current, List<AdvisorPermission> requested) {
    UUID advisorId = current.advisorId();
    var now = clock.instant();
    access.saveRevision(companyId, advisorId, current.revision());
    for (var permission : current.permissions()) {
      if (!requested.contains(permission)) {
        access.revoke(companyId, advisorId, permission, now);
        audit.advisorChanged(
            companyId, actorId, advisorId, AuditOperation.ADVISOR_REVOKED, permission);
      }
    }
    for (var permission : requested) {
      if (!current.permissions().contains(permission)) {
        access.grant(companyId, advisorId, permission, now);
        audit.advisorChanged(
            companyId, actorId, advisorId, AuditOperation.ADVISOR_GRANTED, permission);
      }
    }
    return new AdvisorAccess(advisorId, current.revision() + 1, requested);
  }
}
