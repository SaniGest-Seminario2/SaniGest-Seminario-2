package com.sanigest.sales.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.sanigest.sales.domain.model.SaleLine;
import java.math.BigDecimal;
import java.util.UUID;

public record SaleLineResponse(
    UUID id,
    UUID catalogItemId,
    String itemName,
    String itemDescription,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal unitPrice,
    BigDecimal quantity,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal subtotal) {

  static SaleLineResponse from(SaleLine line) {
    return new SaleLineResponse(
        line.id(),
        line.catalogItemId(),
        line.itemName(),
        line.itemDescription(),
        line.unitPrice(),
        line.quantity(),
        line.subtotal());
  }
}
