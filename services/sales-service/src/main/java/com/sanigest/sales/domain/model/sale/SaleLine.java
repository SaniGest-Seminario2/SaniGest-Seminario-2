package com.sanigest.sales.domain.model.sale;

import com.sanigest.sales.domain.model.catalog.CatalogItemSnapshot;
import com.sanigest.sales.domain.model.catalog.SnapshotText;
import com.sanigest.sales.domain.model.invoice.InvoiceTaxRates;
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
    BigDecimal subtotal,
    BigDecimal discount,
    BigDecimal taxRate) {

  public SaleLine(
      UUID id,
      UUID saleId,
      UUID catalogItemId,
      String itemName,
      String itemDescription,
      BigDecimal unitPrice,
      BigDecimal quantity,
      BigDecimal subtotal) {
    this(
        id,
        saleId,
        catalogItemId,
        itemName,
        itemDescription,
        unitPrice,
        quantity,
        subtotal,
        null,
        null);
  }

  public static SaleLine fromCatalog(
      UUID id, UUID saleId, CatalogItemSnapshot item, BigDecimal quantity) {
    var exactQuantity = validateQuantity(quantity);
    var price = SaleAmounts.amount(item.price(), "unitPrice");
    return new SaleLine(
        id,
        saleId,
        item.id(),
        item.name(),
        item.description(),
        price,
        exactQuantity,
        price.multiply(exactQuantity));
  }

  public static SaleLine forInvoice(
      UUID id,
      UUID saleId,
      CatalogItemSnapshot item,
      BigDecimal quantity,
      BigDecimal discount,
      BigDecimal unitPrice,
      BigDecimal taxRate) {
    var price = SaleAmounts.unitPrice(unitPrice);
    var appliedDiscount = discount == null ? BigDecimal.ZERO : discount;
    return new SaleLine(
        id,
        saleId,
        item.id(),
        item.name(),
        item.description(),
        price,
        quantity,
        SaleAmounts.rounded(price.multiply(quantity).subtract(appliedDiscount)),
        appliedDiscount,
        taxRate == null ? InvoiceTaxRates.EXEMPT : taxRate);
  }

  public BigDecimal taxAmount() {
    return taxRate == null ? BigDecimal.ZERO : SaleAmounts.rounded(subtotal.multiply(taxRate));
  }

  public BigDecimal total() {
    return SaleAmounts.amount(subtotal.add(taxAmount()), "lineTotal");
  }

  public static BigDecimal validateQuantity(BigDecimal quantity) {
    return SaleAmounts.quantity(quantity);
  }

  public SaleLine {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(saleId, "saleId");
    Objects.requireNonNull(catalogItemId, "catalogItemId");
    itemName = SnapshotText.name(itemName, 200, "itemName");
    itemDescription = SnapshotText.bounded(itemDescription, 1000, "itemDescription");
    quantity = SaleAmounts.quantity(quantity);
    subtotal = SaleAmounts.amount(subtotal, "subtotal");
    if (discount == null && taxRate == null) {
      unitPrice = SaleAmounts.amount(unitPrice, "unitPrice");
      if (subtotal.compareTo(unitPrice.multiply(quantity)) != 0) {
        throw new IllegalArgumentException("subtotal must equal unitPrice multiplied by quantity");
      }
    } else {
      unitPrice = SaleAmounts.unitPrice(unitPrice);
      discount = SaleAmounts.amount(discount, "discount");
      taxRate = SaleAmounts.taxRate(taxRate);
      var net = unitPrice.multiply(quantity).subtract(discount);
      if (net.signum() < 0 || subtotal.compareTo(SaleAmounts.rounded(net)) != 0) {
        throw new IllegalArgumentException("Invalid line discount or subtotal");
      }
    }
  }
}
