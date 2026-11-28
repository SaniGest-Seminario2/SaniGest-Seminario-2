package com.sanigest.inventory.domain.model.stock;

import com.sanigest.inventory.domain.exception.InventoryViolation;
import java.math.BigDecimal;

public final class Quantity {
  public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(3);
  private static final BigDecimal LIMIT = new BigDecimal("1000000000000");

  private Quantity() {}

  public static BigDecimal validate(BigDecimal value, boolean divisible, boolean positive) {
    if (value == null
        || value.signum() < 0
        || (positive && value.signum() == 0)
        || value.scale() > 3
        || value.compareTo(LIMIT) >= 0
        || (!divisible && value.stripTrailingZeros().scale() > 0)) {
      throw InventoryViolation.invalid("INVALID_STOCK_QUANTITY");
    }
    return value.setScale(3);
  }
}
