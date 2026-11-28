package com.sanigest.sales.application.dto.sale;

import com.sanigest.sales.domain.enums.receivable.ReceivableStatus;
import com.sanigest.sales.domain.enums.sale.SaleMode;
import com.sanigest.sales.domain.enums.sale.SaleStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SalePage(List<Item> content, int page, int size, long totalElements) {
  public long totalPages() {
    return Math.ceilDiv(totalElements, size);
  }

  public record Item(
      UUID id,
      String code,
      UUID clientId,
      String clientName,
      LocalDate saleDate,
      SaleMode mode,
      SaleStatus status,
      BigDecimal total,
      UUID invoiceId,
      String invoiceNumber,
      ReceivableStatus paymentStatus,
      BigDecimal outstandingBalance,
      BigDecimal collectedAmount) {}
}
