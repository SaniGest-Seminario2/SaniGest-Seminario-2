package com.sanigest.sales.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.sanigest.sales.domain.enums.SaleMode;
import com.sanigest.sales.domain.enums.SaleStatus;
import com.sanigest.sales.domain.model.Sale;
import java.math.BigDecimal;
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
    String currency) {

  public record Client(UUID id, String name) {}

  public static SaleResponse from(Sale sale) {
    return new SaleResponse(
        sale.id(),
        sale.code(),
        new Client(sale.clientId(), sale.clientName()),
        sale.lines().stream().map(SaleLineResponse::from).toList(),
        sale.saleDate(),
        sale.mode(),
        sale.status(),
        sale.total(),
        sale.currency().getCurrencyCode());
  }
}
