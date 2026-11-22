package com.sanigest.company.domain.model;

import java.time.Instant;
import java.util.UUID;

/** Audit metadata is allowlisted by type. Credentials and free-form payloads are excluded. */
public record CompanyAuditEvent(
    UUID id,
    UUID companyId,
    UUID actorId,
    UUID targetUserId,
    Operation operation,
    Instant occurredAt,
    Result result,
    CompanyRole previousRole,
    CompanyRole newRole,
    MembershipStatus previousStatus,
    MembershipStatus newStatus) {

  public enum Operation {
    ROLE_CHANGED,
    MEMBER_ACTIVATED,
    MEMBER_REVOKED
  }

  public enum Result {
    SUCCESS,
    REJECTED
  }
}
