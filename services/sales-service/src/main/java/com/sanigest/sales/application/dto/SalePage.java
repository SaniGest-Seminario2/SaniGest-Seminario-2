package com.sanigest.sales.application.dto;

import com.sanigest.sales.domain.enums.ReceivableStatus;
import com.sanigest.sales.domain.enums.SaleMode;
import com.sanigest.sales.domain.enums.SaleStatus;
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
      String invoiceNumber,
      ReceivableStatus paymentStatus) {}
}
