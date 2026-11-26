package com.sanigest.inventory.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StockMovement(
    UUID id,
    StockKey key,
    Type movementType,
    Direction direction,
    BigDecimal quantity,
    String unitCode,
    Origin originType,
    UUID originId,
    UUID effectId,
    UUID actorId,
    String reason,
    Instant occurredAt,
    Instant recordedAt,
    long sequence,
    BigDecimal balanceAfter) {
  public enum Type {
    INITIAL_STOCK,
    MANUAL_ADJUSTMENT
  }

  public enum Direction {
    IN,
    OUT
  }

  public enum Origin {
    STOCK_CONFIGURATION,
    MANUAL_ADJUSTMENT
  }

  public StockMovement {
    if (movementType == null || direction == null || originType == null) {
      throw InventoryViolation.invalid("INVALID_STOCK_MOVEMENT");
    }
    quantity = Quantity.validate(quantity, true, true);
    balanceAfter = Quantity.validate(balanceAfter, true, false);
    reason = Warehouse.requiredText(reason, 500);
    if (movementType == Type.INITIAL_STOCK && direction != Direction.IN) {
      throw InventoryViolation.invalid("INVALID_INITIAL_MOVEMENT");
    }
  }
}
