package com.sanigest.inventory.domain.model;

import com.sanigest.inventory.domain.exception.InventoryViolation;
import java.time.Instant;

public record StockPosition(
    StockKey key,
    StockConfiguration configuration,
    StockBalance balance,
    boolean initialized,
    String unitCode,
    boolean divisible,
    String sku,
    String itemName,
    Instant catalogVerifiedAt,
    Instant createdAt,
    Instant updatedAt,
    long revision,
    long movementSequence) {
  public void requireConfigurationChange(StockConfiguration next, boolean initialQuantityProvided) {
    if (initialized && initialQuantityProvided) {
      throw InventoryViolation.conflict("INITIAL_STOCK_ALREADY_REGISTERED");
    }
    if (!next.trackingEnabled() && !balance.empty()) {
      throw InventoryViolation.conflict("INVENTORY_TRACKING_HAS_STOCK");
    }
  }

  public void requireCompatibleUnit(String code, boolean newDivisibility) {
    if (movementSequence > 0 && (!unitCode.equals(code) || divisible != newDivisibility)) {
      throw InventoryViolation.invalid("INVENTORY_UNIT_CHANGED");
    }
  }

  public StockAvailability availability() {
    return StockAvailability.derive(configuration, balance);
  }
}
