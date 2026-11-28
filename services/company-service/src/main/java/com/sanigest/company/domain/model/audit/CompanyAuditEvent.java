package com.sanigest.company.domain.model.audit;

import com.sanigest.company.domain.enums.audit.AuditOperation;
import com.sanigest.company.domain.enums.audit.AuditResult;
import com.sanigest.company.domain.enums.team.CompanyRole;
import com.sanigest.company.domain.enums.team.MembershipStatus;
import com.sanigest.company.domain.model.advisor.AdvisorPermission;
import java.time.Instant;
import java.util.UUID;

/** Audit metadata is allowlisted by type. Credentials and free-form payloads are excluded. */
public record CompanyAuditEvent(
    UUID id,
    UUID companyId,
    UUID actorId,
    UUID targetUserId,
    AuditOperation operation,
    Instant occurredAt,
    AuditResult result,
    CompanyRole previousRole,
    CompanyRole newRole,
    MembershipStatus previousStatus,
    MembershipStatus newStatus,
    AdvisorPermission advisorPermission,
    String correlationId,
    UUID invitationId) {
  public UUID resourceId() {
    return invitationId == null ? targetUserId : invitationId;
  }
}
