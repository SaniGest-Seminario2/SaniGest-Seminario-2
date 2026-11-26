package com.sanigest.inventory.application.dto;

import com.sanigest.inventory.domain.InventoryViolation;
import com.sanigest.inventory.domain.StockAvailability;
import com.sanigest.inventory.domain.StockMovement;
import com.sanigest.inventory.domain.StockPosition;
import com.sanigest.inventory.domain.Warehouse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class InventoryQueries {
  private InventoryQueries() {}

  public enum Sort {
    CODE,
    NAME,
    UPDATED_AT,
    OCCURRED_AT,
    QUANTITY,
    AVAILABLE
  }

  public enum Order {
    ASC,
    DESC
  }

  public record Paging(int page, int size, Sort sort, Order order) {
    public Paging {
      if (page < 0 || size < 1 || size > 100 || sort == null || order == null) {
        throw InventoryViolation.invalid("INVALID_PAGINATION");
      }
    }

    public long offset() {
      return (long) page * size;
    }
  }

  public record Page<T>(List<T> items, long total, int page, int size) {}

  public record Warehouses(String search, Warehouse.Status status, Paging paging) {}

  public record StockEntry(StockPosition position, String warehouseName) {}

  public record Stock(
      UUID warehouseId,
      UUID catalogItemId,
      StockAvailability availability,
      Boolean trackingEnabled,
      String search,
      Paging paging,
      List<UUID> catalogItemIds) {
    public Stock {
      if (catalogItemIds != null && catalogItemIds.stream().anyMatch(java.util.Objects::isNull)) {
        throw InventoryViolation.invalid("INVALID_STOCK_BATCH");
      }
      catalogItemIds = catalogItemIds == null ? List.of() : List.copyOf(catalogItemIds);
      if (!catalogItemIds.isEmpty()
          && (warehouseId == null || catalogItemId != null || catalogItemIds.size() > 100)) {
        throw InventoryViolation.invalid("INVALID_STOCK_BATCH");
      }
    }
  }

  public record Movements(
      UUID warehouseId,
      UUID catalogItemId,
      StockMovement.Type movementType,
      StockMovement.Direction direction,
      StockMovement.Origin originType,
      UUID originId,
      Instant dateFrom,
      Instant dateTo,
      Paging paging) {
    public Movements {
      if (dateFrom != null && dateTo != null && !dateFrom.isBefore(dateTo)) {
        throw InventoryViolation.invalid("INVALID_DATE_RANGE");
      }
    }
  }

  public record Summary(
      long activeWarehouses,
      long trackedPositions,
      long availablePositions,
      long lowPositions,
      long outPositions,
      long untrackedConfigurations) {}
}
