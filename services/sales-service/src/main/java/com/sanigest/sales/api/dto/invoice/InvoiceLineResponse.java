package com.sanigest.sales.api.dto.invoice;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.sanigest.sales.domain.model.invoice.InternalInvoiceLine;
import java.math.BigDecimal;
import java.util.UUID;

public record InvoiceLineResponse(
    UUID id,
    UUID catalogItemId,
    String itemName,
    @JsonInclude(JsonInclude.Include.NON_NULL) String sku,
    String itemDescription,
    BigDecimal quantity,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal unitPrice,
    @JsonInclude(JsonInclude.Include.NON_NULL) @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal discount,
    @JsonInclude(JsonInclude.Include.NON_NULL) @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal taxRate,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal subtotal,
    @JsonInclude(JsonInclude.Include.NON_NULL) @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal taxAmount,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal total) {

  static InvoiceLineResponse from(InternalInvoiceLine line) {
    return new InvoiceLineResponse(
        line.id(),
        line.catalogItemId(),
        line.itemName(),
        line.sku(),
        line.itemDescription(),
        line.quantity(),
        line.unitPrice(),
        line.discount(),
        line.taxRate(),
        line.subtotal(),
        line.taxRate() == null ? null : line.taxAmount(),
        line.total());
  }
}
