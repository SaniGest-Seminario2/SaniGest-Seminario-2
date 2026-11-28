package com.sanigest.security.api.dto.audit;

import com.sanigest.web.application.dto.audit.AuditPage.Filters;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record CompanyAuditParameters(
    Instant from,
    Instant to,
    UUID actorId,
    String operation,
    String resourceType,
    UUID resourceId,
    @Schema(defaultValue = "0") Integer page,
    @Schema(defaultValue = "20") Integer size) {
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
