package com.sanigest.sales.infrastructure.persistence.jpa.sale;

import com.sanigest.sales.domain.model.sale.SaleLine;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "sale_lines", schema = "sales")
class SaleLineRow {
  @Id private UUID id;

  @Column(nullable = false)
  private int position;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "sale_id", nullable = false)
  private SaleRow sale;

  @Column(nullable = false)
  private UUID catalogItemId;

  @Column(nullable = false, length = 200)
  private String itemName;

  @Column(length = 1000)
  private String itemDescription;

  @Column(nullable = false, precision = 19, scale = 4)
  private BigDecimal unitPrice;

  private BigDecimal discount;

  private BigDecimal taxRate;

  @Column(nullable = false, precision = 15, scale = 3)
  private BigDecimal quantity;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal subtotal;

  protected SaleLineRow() {}

  SaleLineRow(SaleRow sale, SaleLine line, int position) {
    this.position = position;
    this.sale = sale;
    id = line.id();
    catalogItemId = line.catalogItemId();
    itemName = line.itemName();
    itemDescription = line.itemDescription();
    unitPrice = line.unitPrice();
    quantity = line.quantity();
    subtotal = line.subtotal();
    discount = line.discount();
    taxRate = line.taxRate();
  }

  SaleLine toDomain(UUID saleId) {
    return new SaleLine(
        id,
        saleId,
        catalogItemId,
        itemName,
        itemDescription,
        unitPrice,
        quantity,
        subtotal,
        discount,
        taxRate);
  }
}
