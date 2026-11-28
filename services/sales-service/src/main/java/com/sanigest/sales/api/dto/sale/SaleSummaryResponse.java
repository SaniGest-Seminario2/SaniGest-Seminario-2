package com.sanigest.sales.api.dto.sale;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.sanigest.sales.application.dto.sale.SaleTotals;
import java.math.BigDecimal;

public record SaleSummaryResponse(
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalSales,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalCash,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalCredit,
    long operations,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal averageTicket,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal outstandingBalance) {
  public static SaleSummaryResponse from(SaleTotals totals) {
    return new SaleSummaryResponse(
        totals.totalSales(),
        totals.totalCash(),
        totals.totalCredit(),
        totals.operations(),
        totals.averageTicket(),
        totals.outstandingBalance());
  }
}
