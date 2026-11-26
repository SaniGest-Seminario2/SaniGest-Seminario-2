package com.sanigest.inventory.api.dto;

import com.sanigest.inventory.application.dto.InventoryQueries.Page;
import com.sanigest.inventory.application.dto.InventoryQueries.Summary;
import com.sanigest.inventory.application.port.out.integration.CatalogInventoryClient.Item;
import com.sanigest.inventory.domain.StockAvailability;
import com.sanigest.inventory.domain.StockKey;
import com.sanigest.inventory.domain.StockMovement;
import com.sanigest.inventory.domain.StockPosition;
import com.sanigest.inventory.domain.Warehouse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public final class InventoryResponses {
  private InventoryResponses() {}

  public record WarehouseView(
      UUID id,
      String code,
      String name,
      String location,
      Warehouse.Status status,
      Instant createdAt,
      Instant updatedAt,
      long revision) {
    public static WarehouseView from(Warehouse value) {
      return new WarehouseView(
          value.id(),
          value.code(),
          value.name(),
          value.location(),
          value.status(),
          value.createdAt(),
          value.updatedAt(),
          value.revision());
    }
  }

  public record StockView(
      UUID catalogItemId,
      UUID warehouseId,
      boolean configured,
      boolean trackingEnabled,
      String onHand,
      String reserved,
      String available,
      String minimumStock,
      String maximumStock,
      String location,
      StockAvailability availability,
      String unitCode,
      String sku,
      String itemName,
      Instant catalogVerifiedAt,
      Instant updatedAt,
      Long revision,
      String warehouseName) {
    public static StockView from(StockPosition value) {
      return from(value, null);
    }

    public static StockView from(StockPosition value, String warehouseName) {
      return new StockView(
          value.key().catalogItemId(),
          value.key().warehouseId(),
          true,
          value.configuration().trackingEnabled(),
          decimal(value.balance().onHand()),
          decimal(value.balance().reserved()),
          decimal(value.balance().available()),
          decimal(value.configuration().minimumStock()),
          decimal(value.configuration().maximumStock()),
          value.configuration().location(),
          value.availability(),
          value.unitCode(),
          value.sku(),
          value.itemName(),
          value.catalogVerifiedAt(),
          value.updatedAt(),
          value.revision(),
          warehouseName);
    }

    public static StockView absent(StockKey key, String warehouseName, Item item) {
      return new StockView(
          key.catalogItemId(),
          key.warehouseId(),
          false,
          false,
          null,
          null,
          null,
          null,
          null,
          null,
          "SERVICE".equals(item.type())
              ? StockAvailability.NOT_APPLICABLE
              : StockAvailability.NOT_TRACKED,
          item.unitCode(),
          item.sku(),
          item.name(),
          null,
          null,
          null,
          warehouseName);
    }
  }

  public record MovementView(
      UUID id,
      UUID catalogItemId,
      UUID warehouseId,
      StockMovement.Type movementType,
      StockMovement.Direction direction,
      String quantity,
      String unitCode,
      StockMovement.Origin originType,
      UUID originId,
      UUID effectId,
      UUID actorId,
      String reason,
      Instant occurredAt,
      Instant recordedAt,
      long sequence,
      String balanceAfter) {
    public static MovementView from(StockMovement value) {
      return new MovementView(
          value.id(),
          value.key().catalogItemId(),
          value.key().warehouseId(),
          value.movementType(),
          value.direction(),
          decimal(value.quantity()),
          value.unitCode(),
          value.originType(),
          value.originId(),
          value.effectId(),
          value.actorId(),
          value.reason(),
          value.occurredAt(),
          value.recordedAt(),
          value.sequence(),
          decimal(value.balanceAfter()));
    }
  }

  public record SummaryView(
      long activeWarehouses,
      long trackedPositions,
      long availablePositions,
      long lowPositions,
      long outPositions,
      long untrackedConfigurations) {
    public static SummaryView from(Summary value) {
      return new SummaryView(
          value.activeWarehouses(),
          value.trackedPositions(),
          value.availablePositions(),
          value.lowPositions(),
          value.outPositions(),
          value.untrackedConfigurations());
    }
  }

  public record PageView<T>(
      List<T> content, int page, int size, long totalElements, long totalPages) {
    public static <T, R> PageView<R> from(Page<T> page, Function<T, R> mapper) {
      return new PageView<>(
          page.items().stream().map(mapper).toList(),
          page.page(),
          page.size(),
          page.total(),
          page.total() == 0 ? 0 : 1 + (page.total() - 1) / page.size());
    }
  }

  private static String decimal(BigDecimal value) {
    return value == null ? null : value.toPlainString();
  }
}
