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
    BigDecimal subtotal,
    BigDecimal discount,
    BigDecimal taxRate) {

  public InternalInvoiceLine(
      UUID id,
      UUID itemId,
      String name,
      String description,
      BigDecimal price,
      BigDecimal quantity,
      BigDecimal subtotal) {
    this(id, itemId, name, description, price, quantity, subtotal, null, null);
  }

  public BigDecimal taxAmount() {
    return taxRate == null ? BigDecimal.ZERO : SaleAmounts.rounded(subtotal.multiply(taxRate));
  }

  public BigDecimal total() {
    return SaleAmounts.amount(subtotal.add(taxAmount()), "lineTotal");
  }

  public InternalInvoiceLine {
    Objects.requireNonNull(id);
    Objects.requireNonNull(catalogItemId);
    itemName = SnapshotText.name(itemName, 200, "itemName");
    itemDescription = SnapshotText.bounded(itemDescription, 1000, "itemDescription");
    if ((discount == null) != (taxRate == null)) {
      throw new IllegalArgumentException("Discount and tax rate must be recorded together");
    }
    unitPrice =
        discount == null
            ? SaleAmounts.amount(unitPrice, "unitPrice")
            : SaleAmounts.unitPrice(unitPrice);
    quantity = SaleAmounts.quantity(quantity);
    subtotal = SaleAmounts.amount(subtotal, "subtotal");
    var net = unitPrice.multiply(quantity);
    if (discount != null) {
      discount = SaleAmounts.amount(discount, "discount");
      taxRate = SaleAmounts.taxRate(taxRate);
      net = net.subtract(discount);
      if (net.signum() < 0) {
        throw new IllegalArgumentException("Invoice discount exceeds line amount");
      }
      net = SaleAmounts.rounded(net);
    }
    if (subtotal.compareTo(net) != 0) {
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
        line.subtotal(),
        line.discount(),
        line.taxRate());
  }
}
