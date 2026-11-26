package com.sanigest.sales.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class SaleAmounts {
  private static final BigDecimal AMOUNT_LIMIT = BigDecimal.TEN.pow(17);
  private static final BigDecimal QUANTITY_LIMIT = BigDecimal.TEN.pow(12);

  private SaleAmounts() {}

  static BigDecimal amount(BigDecimal value, String field) {
    return exactDecimal(value, field, AMOUNT_LIMIT, 2);
  }

  static BigDecimal unitPrice(BigDecimal value) {
    return exactDecimal(value, "unitPrice", BigDecimal.TEN.pow(15), 4);
  }

  static BigDecimal rounded(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_UP);
  }

  static BigDecimal taxRate(BigDecimal value) {
    return exactDecimal(value, "taxRate", BigDecimal.ONE, 6);
  }

  static BigDecimal quantity(BigDecimal value) {
    BigDecimal quantity = exactDecimal(value, "quantity", QUANTITY_LIMIT, 3);
    if (quantity.signum() == 0) {
      throw new IllegalArgumentException("quantity must be positive");
    }
    return quantity;
  }

  private static BigDecimal exactDecimal(
      BigDecimal value, String field, BigDecimal limit, int scale) {
    if (value == null
        || value.signum() < 0
        || value.compareTo(limit) >= 0
        || value.stripTrailingZeros().scale() > scale) {
      throw new IllegalArgumentException(field + " is outside the supported decimal range");
    }
    return value.setScale(scale, RoundingMode.UNNECESSARY);
  }
}
