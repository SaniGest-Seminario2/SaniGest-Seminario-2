package com.sanigest.sales.domain.model;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record SaleLine(
    UUID id,
    UUID saleId,
    UUID catalogItemId,
    String itemName,
    String itemDescription,
    BigDecimal unitPrice,
    BigDecimal quantity,
    BigDecimal subtotal) {

  public SaleLine {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(saleId, "saleId");
    Objects.requireNonNull(catalogItemId, "catalogItemId");
    itemName = SnapshotText.name(itemName, 200, "itemName");
    itemDescription = SnapshotText.bounded(itemDescription, 1000, "itemDescription");
    unitPrice = SaleAmounts.amount(unitPrice, "unitPrice");
    quantity = SaleAmounts.quantity(quantity);
    subtotal = SaleAmounts.amount(subtotal, "subtotal");
    if (subtotal.compareTo(unitPrice.multiply(quantity)) != 0) {
      throw new IllegalArgumentException("subtotal must equal unitPrice multiplied by quantity");
    }
  }
}
