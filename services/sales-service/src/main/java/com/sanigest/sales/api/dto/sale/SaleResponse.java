package com.sanigest.sales.api.dto.sale;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.sanigest.sales.domain.enums.sale.SaleMode;
import com.sanigest.sales.domain.enums.sale.SaleStatus;
import com.sanigest.sales.domain.model.sale.Sale;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SaleResponse(
    UUID id,
    String code,
    Client client,
    List<SaleLineResponse> lines,
    LocalDate saleDate,
    SaleMode mode,
    SaleStatus status,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal total,
    String currency,
    @JsonInclude(JsonInclude.Include.NON_NULL) Cancellation cancellation) {

  public record Client(UUID id, String name) {}

  public record Cancellation(UUID cancelledBy, Instant cancelledAt, String cancelReason) {}

  public static SaleResponse from(Sale sale) {
    var cancellation = sale.cancellation();
    return new SaleResponse(
        sale.id(),
        sale.code(),
        new Client(sale.clientId(), sale.clientName()),
        sale.lines().stream().map(SaleLineResponse::from).toList(),
        sale.saleDate(),
        sale.mode(),
        sale.status(),
        sale.total(),
        sale.currency().getCurrencyCode(),
        cancellation == null
            ? null
            : new Cancellation(
                cancellation.cancelledBy(),
                cancellation.cancelledAt(),
                cancellation.cancelReason()));
  }
}
