package com.sanigest.sales.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

public record CatalogItemSnapshot(
    UUID id,
    String name,
    Type type,
    String description,
    BigDecimal price,
    String currency,
    boolean active,
    BigDecimal taxRate) {
  public CatalogItemSnapshot(
      UUID id,
      String name,
      Type type,
      String description,
      BigDecimal price,
      String currency,
      boolean active) {
    this(id, name, type, description, price, currency, active, null);
  }

  public enum Type {
    PRODUCT,
    SERVICE
  }
}
