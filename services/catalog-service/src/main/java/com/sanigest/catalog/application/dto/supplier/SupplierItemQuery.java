package com.sanigest.catalog.application.dto.supplier;

import com.sanigest.catalog.domain.enums.supplier.SupplierStatus;
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
