package com.sanigest.sales.api.dto.receivable;

import com.sanigest.sales.application.dto.receivable.ReceivableDetail;
import com.sanigest.sales.domain.enums.receivable.ReceivableOrigin;
import com.sanigest.sales.domain.enums.receivable.ReceivableStatus;
import java.time.LocalDate;
import java.util.UUID;

public record ReceivableDetailResponse(
    UUID id,
    UUID saleId,
    Client client,
    ReceivableOrigin origin,
    String totalAmount,
    String balance,
    String currency,
    LocalDate dueDate,
    ReceivableStatus status,
    String saleCode,
    UUID invoiceId,
    String invoiceNumber,
    String outstandingBalance,
    String collectedAmount) {

  public static ReceivableDetailResponse from(ReceivableDetail detail) {
    var receivable = detail.receivable();
    return new ReceivableDetailResponse(
        receivable.id(),
        receivable.saleId(),
        new Client(receivable.clientId(), receivable.clientName()),
        receivable.origin(),
        receivable.totalAmount().toPlainString(),
        receivable.balance().toPlainString(),
        receivable.currency(),
        receivable.dueDate(),
        receivable.status(),
        detail.saleCode(),
        detail.invoiceId(),
        detail.invoiceNumber(),
        receivable.collectibleBalance().toPlainString(),
        receivable.collectedAmount().toPlainString());
  }

  public record Client(UUID id, String name) {}
}
