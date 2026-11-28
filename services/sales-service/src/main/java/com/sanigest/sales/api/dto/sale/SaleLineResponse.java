package com.sanigest.sales.api.dto.sale;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.sanigest.sales.domain.model.sale.SaleLine;
import java.math.BigDecimal;
import java.util.UUID;

public record SaleLineResponse(
    UUID id,
    UUID catalogItemId,
    String itemName,
    String itemDescription,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal unitPrice,
    BigDecimal quantity,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal subtotal,
    @JsonInclude(JsonInclude.Include.NON_NULL) @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal discount,
    @JsonInclude(JsonInclude.Include.NON_NULL) @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal taxRate,
    @JsonInclude(JsonInclude.Include.NON_NULL) @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal taxAmount) {

  static SaleLineResponse from(SaleLine line) {
    return new SaleLineResponse(
        line.id(),
        line.catalogItemId(),
        line.itemName(),
        line.itemDescription(),
        line.unitPrice(),
        line.quantity(),
        line.subtotal(),
        line.discount(),
        line.taxRate(),
        line.taxRate() == null ? null : line.taxAmount());
  }
}
