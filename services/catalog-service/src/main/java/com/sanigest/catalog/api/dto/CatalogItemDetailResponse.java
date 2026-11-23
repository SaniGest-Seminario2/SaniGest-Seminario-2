package com.sanigest.catalog.api.dto;

import com.sanigest.catalog.domain.enums.CatalogItemType;
import com.sanigest.catalog.domain.model.CatalogItem;
import java.math.BigDecimal;
import java.util.UUID;

public record CatalogItemDetailResponse(
    UUID id,
    String name,
    CatalogItemType type,
    String description,
    BigDecimal price,
    String currency,
    boolean active) {
  public static CatalogItemDetailResponse from(CatalogItem item) {
    return new CatalogItemDetailResponse(
        item.id(),
        item.name(),
        item.type(),
        item.description(),
        item.price(),
        item.currency(),
        item.active());
  }
}
