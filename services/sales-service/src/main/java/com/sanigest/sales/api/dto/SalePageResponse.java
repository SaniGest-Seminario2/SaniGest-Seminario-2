package com.sanigest.sales.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.sanigest.sales.application.dto.SalePage;
import com.sanigest.sales.domain.enums.ReceivableStatus;
import com.sanigest.sales.domain.enums.SaleMode;
import com.sanigest.sales.domain.enums.SaleStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SalePageResponse(
    List<Item> content, int page, int size, long totalElements, long totalPages) {
  public static SalePageResponse from(SalePage result) {
    return new SalePageResponse(
        result.content().stream().map(Item::from).toList(),
        result.page(),
        result.size(),
        result.totalElements(),
        result.totalPages());
  }

  public record Item(
      UUID id,
      String code,
      SaleResponse.Client client,
      LocalDate saleDate,
      SaleMode mode,
      SaleStatus status,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal total,
      String currency,
      String invoiceNumber,
      ReceivableStatus paymentStatus,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal outstandingBalance,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal collectedAmount) {
    private static Item from(SalePage.Item item) {
      return new Item(
          item.id(),
          item.code(),
          new SaleResponse.Client(item.clientId(), item.clientName()),
          item.saleDate(),
          item.mode(),
          item.status(),
          item.total(),
          "DOP",
          item.invoiceNumber(),
          item.paymentStatus(),
          item.outstandingBalance(),
          item.collectedAmount());
    }
  }
}
