package com.sanigest.sales.application.dto;

import com.sanigest.sales.domain.enums.SaleMode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateSaleCommand(
    UUID clientId, List<Line> lines, LocalDate saleDate, SaleMode mode, LocalDate dueDate) {
  public CreateSaleCommand(UUID clientId, List<Line> lines, LocalDate saleDate, SaleMode mode) {
    this(clientId, lines, saleDate, mode, null);
  }

  public static final int MAX_LINES = 100;

  public record Line(UUID catalogItemId, BigDecimal quantity, BigDecimal discount) {
    public Line(UUID catalogItemId, BigDecimal quantity) {
      this(catalogItemId, quantity, null);
    }
  }
}
