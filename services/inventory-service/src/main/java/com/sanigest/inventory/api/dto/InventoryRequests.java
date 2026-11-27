package com.sanigest.inventory.api.dto;

import com.sanigest.inventory.application.dto.StockCommands;
import com.sanigest.inventory.application.usecase.WarehouseOperations;
import com.sanigest.inventory.domain.InventoryViolation;
import com.sanigest.inventory.domain.StockConfiguration;
import com.sanigest.inventory.domain.StockMovement;
import com.sanigest.inventory.domain.Warehouse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

public final class InventoryRequests {
  private InventoryRequests() {}

  public record CreateWarehouse(@NotBlank String code, @NotBlank String name, String location) {
    public WarehouseOperations.Create command() {
      return new WarehouseOperations.Create(code, name, location);
    }
  }

  public record Configure(
      @NotNull Boolean trackingEnabled,
      JsonNode minimumStock,
      JsonNode maximumStock,
      String location,
      JsonNode initialQuantity,
      @PositiveOrZero Long revision) {
    public StockCommands.Configure command() {
      return new StockCommands.Configure(
          new StockConfiguration(
              trackingEnabled, decimal(minimumStock), decimal(maximumStock), location),
          decimal(initialQuantity),
          revision);
    }
  }

  public record Adjust(
      @NotNull UUID catalogItemId,
      @NotNull UUID warehouseId,
      @NotNull StockMovement.Direction direction,
      @NotNull JsonNode quantity,
      @NotBlank String reason) {
    public StockCommands.Adjust command() {
      return new StockCommands.Adjust(direction, decimal(quantity), reason);
    }
  }

  public record PurchaseReceipt(
      @NotNull UUID expenseId,
      @NotNull UUID catalogItemId,
      @NotNull UUID warehouseId,
      @NotNull JsonNode quantity) {
    public BigDecimal receivedQuantity() {
      return decimal(quantity);
    }
  }

  public static WarehouseOperations.Patch patch(ObjectNode body) {
    if (body.isEmpty()
        || !Set.of("code", "name", "location", "status", "revision")
            .containsAll(body.propertyNames())) {
      throw InventoryViolation.invalid("INVALID_WAREHOUSE_PATCH");
    }
    var status = field(body, "status");
    var revision = body.get("revision");
    if (revision != null
        && (!revision.isIntegralNumber()
            || !revision.canConvertToLong()
            || revision.longValue() < 0)) {
      throw InventoryViolation.invalid("INVALID_REVISION");
    }
    Warehouse.Status state = null;
    if (status.value() != null) {
      try {
        state = Warehouse.Status.valueOf(status.value());
      } catch (IllegalArgumentException invalid) {
        throw InventoryViolation.invalid("INVALID_WAREHOUSE_STATUS");
      }
    }
    return new WarehouseOperations.Patch(
        field(body, "code"),
        field(body, "name"),
        field(body, "location"),
        new WarehouseOperations.Field<>(status.present(), state),
        revision == null ? null : revision.longValue());
  }

  private static WarehouseOperations.Field<String> field(ObjectNode body, String name) {
    var node = body.get(name);
    if (node != null && !node.isNull() && !node.isString()) {
      throw InventoryViolation.invalid("INVALID_WAREHOUSE_PATCH");
    }
    return new WarehouseOperations.Field<>(
        node != null, node == null || node.isNull() ? null : node.stringValue());
  }

  private static BigDecimal decimal(JsonNode value) {
    if (value == null || value.isNull()) {
      return null;
    }
    if (!value.isString() || !value.stringValue().matches("-?[0-9]{1,12}(\\.[0-9]{1,3})?")) {
      throw InventoryViolation.invalid("INVALID_STOCK_QUANTITY");
    }
    return new BigDecimal(value.stringValue());
  }
}
