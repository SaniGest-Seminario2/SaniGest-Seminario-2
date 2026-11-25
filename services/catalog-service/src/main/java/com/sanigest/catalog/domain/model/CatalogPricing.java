package com.sanigest.catalog.domain.model;

import com.sanigest.catalog.domain.exception.InvalidCatalogItem;
import java.math.BigDecimal;
import java.math.RoundingMode;

public record CatalogPricing(
    BigDecimal unitTaxAmount,
    BigDecimal finalPrice,
    BigDecimal grossProfit,
    BigDecimal grossMargin) {

  private static final BigDecimal LIMIT = BigDecimal.TEN.pow(15);

  public static BigDecimal unitAmount(BigDecimal amount) {
    if (amount == null
        || amount.signum() < 0
        || amount.compareTo(LIMIT) >= 0
        || amount.scale() > 4) {
      throw new InvalidCatalogItem("Invalid unit amount");
    }
    return amount.setScale(4, RoundingMode.UNNECESSARY);
  }

  public static CatalogPricing calculate(BigDecimal price, BigDecimal cost, BigDecimal rate) {
    var tax = rate == null ? null : price.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    var total = tax == null ? null : price.add(tax).setScale(2, RoundingMode.HALF_UP);
    var profit = cost == null ? null : price.subtract(cost);
    var margin =
        profit == null || price.signum() == 0
            ? null
            : profit.movePointRight(2).divide(price, 2, RoundingMode.HALF_UP);
    return new CatalogPricing(tax, total, profit, margin);
  }
}
