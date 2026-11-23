package com.sanigest.sales.api.dto;

import com.sanigest.sales.domain.enums.ReceivableOrigin;
import com.sanigest.sales.domain.enums.ReceivableStatus;
import com.sanigest.sales.domain.model.Receivable;
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
    ReceivableStatus status) {

  public static ReceivableDetailResponse from(Receivable receivable) {
    return new ReceivableDetailResponse(
        receivable.id(),
        receivable.saleId(),
        new Client(receivable.clientId(), receivable.clientName()),
        receivable.origin(),
        receivable.totalAmount().toPlainString(),
        receivable.balance().toPlainString(),
        receivable.currency(),
        receivable.dueDate(),
        receivable.status());
  }

  public record Client(UUID id, String name) {}
}
