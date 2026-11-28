package com.sanigest.security;

import com.sanigest.web.audit.AuditPage.Filters;
import java.time.Instant;
import java.util.UUID;

public record CompanyAuditParameters(
    Instant from,
    Instant to,
    UUID actorId,
    String operation,
    String resourceType,
    UUID resourceId,
    Integer page,
    Integer size) {
  public Filters filters() {
    return new Filters(
        from,
        to,
        actorId,
        operation,
        resourceType,
        resourceId,
        page == null ? 0 : page,
        size == null ? 20 : size);
  }
}
