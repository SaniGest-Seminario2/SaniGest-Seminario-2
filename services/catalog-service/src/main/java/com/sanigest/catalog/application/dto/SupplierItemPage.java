package com.sanigest.catalog.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SupplierItemPage(List<Entry> content, int page, int size, long totalElements) {
  public SupplierItemPage {
    content = List.copyOf(content);
  }

  public long totalPages() {
    return Math.ceilDiv(totalElements, size);
  }

  public record Entry(Party party, Offer offer) {}

  public record Party(UUID id, String name, String type, boolean active) {}

  public record Offer(
      BigDecimal offeredCost,
      String supplierReference,
      boolean primary,
      Instant validFrom,
      Instant validUntil) {}
}
