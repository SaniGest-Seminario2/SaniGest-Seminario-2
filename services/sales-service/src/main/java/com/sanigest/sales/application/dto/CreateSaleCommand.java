package com.sanigest.sales.application.dto;

import com.sanigest.sales.domain.enums.RequestedReceiptType;
import com.sanigest.sales.domain.enums.SaleMode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateSaleCommand(
    UUID clientId,
    List<Line> lines,
    LocalDate saleDate,
    SaleMode mode,
    LocalDate dueDate,
    RequestedReceiptType requestedReceiptType,
    String notes) {
  public CreateSaleCommand(
      UUID clientId, List<Line> lines, LocalDate saleDate, SaleMode mode, LocalDate dueDate) {
    this(clientId, lines, saleDate, mode, dueDate, null, null);
  }

  public CreateSaleCommand(UUID clientId, List<Line> lines, LocalDate saleDate, SaleMode mode) {
    this(clientId, lines, saleDate, mode, null);
  }

  public static final int MAX_LINES = 100;

  public boolean hasInvoiceFields() {
    return requestedReceiptType != null
        || notes != null
        || lines.stream().anyMatch(line -> line.unitPrice() != null || line.taxRate() != null);
  }

  public record Line(
      UUID catalogItemId,
      BigDecimal quantity,
      BigDecimal discount,
      BigDecimal unitPrice,
      BigDecimal taxRate) {
    public Line(UUID catalogItemId, BigDecimal quantity, BigDecimal discount) {
      this(catalogItemId, quantity, discount, null, null);
    }

    public Line(UUID catalogItemId, BigDecimal quantity) {
      this(catalogItemId, quantity, null);
    }
  }
}
