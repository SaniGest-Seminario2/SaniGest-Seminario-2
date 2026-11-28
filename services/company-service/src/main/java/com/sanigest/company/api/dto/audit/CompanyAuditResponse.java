package com.sanigest.company.api.dto.audit;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sanigest.company.application.dto.audit.AuditPage;
import com.sanigest.company.domain.model.advisor.AdvisorPermission;
import com.sanigest.company.domain.model.audit.CompanyAuditEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CompanyAuditResponse(List<Entry> content, int page, int size, boolean hasNext) {
  public static CompanyAuditResponse from(AuditPage result) {
    return new CompanyAuditResponse(
        result.content().stream().map(Entry::from).toList(),
        result.page(),
        result.size(),
        result.hasNext());
  }

  public record Entry(
      UUID id,
      UUID companyId,
      UUID actorId,
      UUID targetUserId,
      String operation,
      Instant occurredAt,
      String result,
      String previousRole,
      String newRole,
      String previousStatus,
      String newStatus,
      @JsonInclude(JsonInclude.Include.NON_NULL) AdvisorPermission advisorPermission,
      @JsonInclude(JsonInclude.Include.NON_NULL) String correlationId,
      String resourceType,
      UUID resourceId) {

    private static Entry from(CompanyAuditEvent event) {
      return new Entry(
          event.id(),
          event.companyId(),
          event.actorId(),
          event.targetUserId(),
          event.operation().name(),
          event.occurredAt(),
          event.result().name(),
          name(event.previousRole()),
          name(event.newRole()),
          name(event.previousStatus()),
          name(event.newStatus()),
          event.advisorPermission(),
          event.correlationId(),
          event.operation().resourceType(),
          event.resourceId());
    }

    private static String name(Enum<?> value) {
      return value == null ? null : value.name();
    }
  }
}
