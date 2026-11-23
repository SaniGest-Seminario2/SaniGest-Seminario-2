package com.sanigest.catalog.api.dto;

import com.sanigest.catalog.domain.enums.CatalogItemType;
import com.sanigest.catalog.domain.model.CatalogItem;
import java.math.BigDecimal;
import java.util.UUID;

public record CatalogItemResponse(
    UUID id,
    UUID companyId,
    String name,
    CatalogItemType type,
    BigDecimal price,
    String currency,
    boolean active) {
  public static CatalogItemResponse from(CatalogItem item) {
    return new CatalogItemResponse(
        item.id(),
        item.companyId(),
        item.name(),
        item.type(),
        item.price(),
        item.currency(),
        item.active());
  }
}
