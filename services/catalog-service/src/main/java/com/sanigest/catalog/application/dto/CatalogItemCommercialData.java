package com.sanigest.catalog.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CatalogItemCommercialData(
    String categoryName,
    UUID primarySupplierId,
    BigDecimal taxRate,
    LocalDate taxEffectiveFrom,
    LocalDate taxEffectiveUntil,
    Boolean unitDivisible) {
  public CatalogItemCommercialData(
      String categoryName,
      UUID primarySupplierId,
      BigDecimal taxRate,
      LocalDate from,
      LocalDate until) {
    this(categoryName, primarySupplierId, taxRate, from, until, null);
  }

  public static final CatalogItemCommercialData EMPTY =
      new CatalogItemCommercialData(null, null, null, null, null);
}
