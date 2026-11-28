package com.sanigest.web.audit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AuditPage(List<Entry> content, int page, int size, boolean hasNext) {
  public record Entry(
      UUID id,
      UUID actorId,
      String operation,
      String resourceType,
      UUID resourceId,
      Instant occurredAt,
      String result,
      String previousState,
      String state,
      String correlationId) {}

  @FunctionalInterface
  public interface Reader {
    AuditPage read(UUID companyId, Filters filters);
  }

  public record Filters(
      Instant from,
      Instant to,
      UUID actorId,
      String operation,
      String resourceType,
      UUID resourceId,
      int page,
      int size) {
    public Filters {
      if (page < 0 || size < 1 || size > 100 || (from != null && to != null && from.isAfter(to))) {
        throw new IllegalArgumentException("Invalid audit log filters");
      }
    }

    public long offset() {
      return (long) page * size;
    }
  }
}
