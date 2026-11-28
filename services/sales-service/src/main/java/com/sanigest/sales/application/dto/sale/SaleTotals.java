package com.sanigest.sales.application.dto.sale;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record SaleTotals(
    BigDecimal totalSales,
    BigDecimal totalCash,
    BigDecimal totalCredit,
    long operations,
    BigDecimal outstandingBalance) {
  public BigDecimal averageTicket() {
    return operations == 0
        ? null
        : totalSales.divide(BigDecimal.valueOf(operations), 2, RoundingMode.HALF_UP);
  }
}
