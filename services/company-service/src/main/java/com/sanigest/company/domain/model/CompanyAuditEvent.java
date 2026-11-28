package com.sanigest.company.domain.model;

import com.sanigest.company.domain.enums.AuditOperation;
import com.sanigest.company.domain.enums.AuditResult;
import com.sanigest.company.domain.enums.CompanyRole;
import com.sanigest.company.domain.enums.MembershipStatus;
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
