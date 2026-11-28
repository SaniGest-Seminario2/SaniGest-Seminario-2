package com.sanigest.sales.infrastructure.persistence.jpa.sale;

import com.sanigest.sales.domain.enums.sale.SaleMode;
import com.sanigest.sales.domain.enums.sale.SaleStatus;
import com.sanigest.sales.domain.model.sale.Sale;
import com.sanigest.sales.domain.model.sale.SaleCancellation;
import com.sanigest.sales.domain.model.sale.SaleLine;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "sales", schema = "sales")
public class SaleRow {
  @Id private UUID id;

  @Column(length = 26)
  private String code;

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

  private UUID cancelledBy;

  private Instant cancelledAt;

  @Column(length = SaleCancellation.MAX_REASON_LENGTH)
  private String cancelReason;

  @OneToMany(mappedBy = "sale", cascade = CascadeType.PERSIST)
  @OrderBy("position ASC, id ASC")
  private List<SaleLineRow> lines = new ArrayList<>();

  protected SaleRow() {}

  SaleRow(Sale sale) {
    id = sale.id();
    code = sale.code();
    companyId = sale.companyId();
    clientId = sale.clientId();
    clientName = sale.clientName();
    saleDate = sale.saleDate();
    mode = sale.mode();
    status = sale.status();
    total = sale.total();
    if (sale.cancellation() != null) {
      cancelledBy = sale.cancellation().cancelledBy();
      cancelledAt = sale.cancellation().cancelledAt();
      cancelReason = sale.cancellation().cancelReason();
    }
    for (SaleLine line : sale.lines()) {
      lines.add(new SaleLineRow(this, line, lines.size()));
    }
  }

  public Sale toDomain() {
    return new Sale(
        id,
        companyId,
        clientId,
        clientName,
        saleDate,
        mode,
        status,
        total,
        lines.stream().map(line -> line.toDomain(id)).toList(),
        code,
        cancelledBy == null ? null : new SaleCancellation(cancelledBy, cancelledAt, cancelReason));
  }
}
