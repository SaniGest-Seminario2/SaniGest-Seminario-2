package com.sanigest.catalog.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class CatalogItemReferences {
  private CatalogItemReferences() {}

  public record Unit(String code, String name, boolean active, boolean divisible) {}

  public record Tax(
      String code,
      String name,
      BigDecimal rate,
      LocalDate effectiveFrom,
      LocalDate effectiveUntil) {}
}
