package com.sanigest.sales.domain.model.catalog;

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
    BigDecimal taxRate,
    String sku) {
  public CatalogItemSnapshot {
    sku = SnapshotText.bounded(sku, 64, "sku");
  }

  public CatalogItemSnapshot(
      UUID id,
      String name,
      Type type,
      String description,
      BigDecimal price,
      String currency,
      boolean active) {
    this(id, name, type, description, price, currency, active, null, null);
  }

  public enum Type {
    PRODUCT,
    SERVICE
  }
}
