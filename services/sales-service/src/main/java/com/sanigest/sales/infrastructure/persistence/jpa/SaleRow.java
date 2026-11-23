package com.sanigest.sales.infrastructure.persistence.jpa;

import com.sanigest.sales.domain.enums.SaleMode;
import com.sanigest.sales.domain.enums.SaleStatus;
import com.sanigest.sales.domain.model.Sale;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "sales", schema = "sales")
class SaleRow {
  @Id private UUID id;

  @Column(nullable = false)
  private UUID companyId;

  @Column(nullable = false)
  private UUID clientId;

  @Column(nullable = false, length = 250)
  private String clientName;

  @Column(nullable = false)
  private LocalDate saleDate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 6)
  private SaleMode mode;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private SaleStatus status;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal total;

  @OneToMany(mappedBy = "sale", cascade = CascadeType.PERSIST)
  @OrderBy("id ASC")
  private List<SaleLineRow> lines = new ArrayList<>();

  protected SaleRow() {}

  SaleRow(Sale sale) {
    id = sale.id();
    companyId = sale.companyId();
    clientId = sale.clientId();
    clientName = sale.clientName();
    saleDate = sale.saleDate();
    mode = sale.mode();
    status = sale.status();
    total = sale.total();
    sale.lines().forEach(line -> lines.add(new SaleLineRow(this, line)));
  }

  Sale toDomain() {
    return new Sale(
        id,
        companyId,
        clientId,
        clientName,
        saleDate,
        mode,
        status,
        total,
        lines.stream().map(line -> line.toDomain(id)).toList());
  }
}
