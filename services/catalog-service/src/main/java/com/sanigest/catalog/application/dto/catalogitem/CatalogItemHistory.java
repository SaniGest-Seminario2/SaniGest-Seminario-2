package com.sanigest.catalog.application.dto.catalogitem;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CatalogItemHistory(List<Entry> content, int page, int size, long totalElements) {
  public record Entry(
      long id,
      UUID actorId,
      String operation,
      Instant occurredAt,
      String previousValues,
      String newValues) {}
}
