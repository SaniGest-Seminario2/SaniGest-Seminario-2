package com.sanigest.sales.domain.model;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record InternalInvoiceLine(
    UUID id,
    UUID catalogItemId,
    String itemName,
    String itemDescription,
    BigDecimal unitPrice,
    BigDecimal quantity,
    BigDecimal subtotal) {

  public InternalInvoiceLine {
    Objects.requireNonNull(id);
    Objects.requireNonNull(catalogItemId);
    itemName = SnapshotText.name(itemName, 200, "itemName");
    itemDescription = SnapshotText.bounded(itemDescription, 1000, "itemDescription");
    unitPrice = SaleAmounts.amount(unitPrice, "unitPrice");
    quantity = SaleAmounts.quantity(quantity);
    subtotal = SaleAmounts.amount(subtotal, "subtotal");
    if (subtotal.compareTo(unitPrice.multiply(quantity)) != 0) {
      throw new IllegalArgumentException("Invalid invoice line subtotal");
    }
  }

  static InternalInvoiceLine from(SaleLine line) {
    return new InternalInvoiceLine(
        line.id(),
        line.catalogItemId(),
        line.itemName(),
        line.itemDescription(),
        line.unitPrice(),
        line.quantity(),
        line.subtotal());
  }
}
