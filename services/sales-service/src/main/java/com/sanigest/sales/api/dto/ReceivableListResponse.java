package com.sanigest.sales.api.dto;

import com.sanigest.sales.api.dto.ReceivableDetailResponse.Client;
import com.sanigest.sales.application.dto.ReceivableDetail;
import com.sanigest.sales.application.dto.ReceivablePage;
import com.sanigest.sales.domain.enums.ReceivableStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ReceivableListResponse(
    UUID id,
    Client client,
    UUID documentId,
    String documentNumber,
    LocalDate dueDate,
    String totalAmount,
    String balance,
    ReceivableStatus status) {

  public static ReceivableListResponse from(ReceivableDetail detail) {
    var receivable = detail.receivable();
    boolean hasInvoice = detail.invoiceId() != null;
    return new ReceivableListResponse(
        receivable.id(),
        new Client(receivable.clientId(), receivable.clientName()),
        hasInvoice ? detail.invoiceId() : receivable.saleId(),
        hasInvoice ? detail.invoiceNumber() : detail.saleCode(),
        receivable.dueDate(),
        receivable.totalAmount().toPlainString(),
        receivable.balance().toPlainString(),
        receivable.status());
  }

  public record Page(
      List<ReceivableListResponse> content,
      int page,
      int size,
      long totalElements,
      long totalPages) {
    public static Page from(ReceivablePage page) {
      return new Page(
          page.content().stream().map(ReceivableListResponse::from).toList(),
          page.page(),
          page.size(),
          page.totalElements(),
          Math.ceilDiv(page.totalElements(), page.size()));
    }
  }
}
