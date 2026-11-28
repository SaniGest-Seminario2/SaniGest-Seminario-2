package com.sanigest.sales.api.dto.sale;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.sanigest.sales.application.dto.sale.SaleDetail;
import com.sanigest.sales.domain.enums.receivable.ReceivableStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SaleDetailResponse(
    @JsonUnwrapped SaleResponse sale,
    UUID invoiceId,
    String invoiceNumber,
    LocalDate dueDate,
    ReceivableStatus paymentStatus,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal outstandingBalance,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal collectedAmount) {
  public static SaleDetailResponse from(SaleDetail detail) {
    var financials = detail.financials();
    return new SaleDetailResponse(
        SaleResponse.from(detail.sale()),
        financials.invoiceId(),
        financials.invoiceNumber(),
        financials.dueDate(),
        financials.paymentStatus(),
        financials.outstandingBalance(),
        financials.collectedAmount());
  }
}
