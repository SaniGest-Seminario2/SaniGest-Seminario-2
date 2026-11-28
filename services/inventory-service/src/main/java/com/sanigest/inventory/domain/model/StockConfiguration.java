package com.sanigest.inventory.domain.model;

import com.sanigest.inventory.domain.exception.InventoryViolation;
import java.math.BigDecimal;

public record StockConfiguration(
    boolean trackingEnabled, BigDecimal minimumStock, BigDecimal maximumStock, String location) {
  public StockConfiguration {
    minimumStock =
        Quantity.validate(minimumStock == null ? Quantity.ZERO : minimumStock, true, false);
    if (maximumStock != null) {
      maximumStock = Quantity.validate(maximumStock, true, false);
      if (maximumStock.compareTo(minimumStock) < 0) {
        throw InventoryViolation.invalid("INVALID_STOCK_THRESHOLDS");
      }
    }
    location = Warehouse.optionalText(location, 240);
  }

  public void validateUnit(boolean divisible) {
    Quantity.validate(minimumStock, divisible, false);
    if (maximumStock != null) {
      Quantity.validate(maximumStock, divisible, false);
    }
  }
}
