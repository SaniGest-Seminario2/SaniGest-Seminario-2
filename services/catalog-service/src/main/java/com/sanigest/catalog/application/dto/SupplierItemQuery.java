package com.sanigest.catalog.application.dto;

import com.sanigest.catalog.domain.enums.SupplierStatus;
import java.util.UUID;

public record SupplierItemQuery(
    UUID companyId,
    UUID ownerId,
    Direction direction,
    String search,
    SupplierStatus status,
    int page,
    int size) {
  public enum Direction {
    SUPPLIED_ITEMS,
    ITEM_SUPPLIERS
  }
}
