package com.sanigest.sales.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.sanigest.sales.domain.model.InternalInvoice;
import com.sanigest.sales.domain.model.InternalInvoice.DocumentType;
import com.sanigest.sales.domain.model.InternalInvoice.FiscalClassification;
import com.sanigest.sales.domain.model.InternalInvoice.Status;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record InvoiceResponse(
    UUID id,
    String number,
    UUID saleId,
    DocumentType documentType,
    FiscalClassification fiscalClassification,
    Status status,
    Instant issuedAt,
    LocalDate saleDate,
    Party company,
    Party client,
    List<SaleLineResponse> lines,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal total,
    String currency) {

  public record Party(UUID id, String name) {}

  public static InvoiceResponse from(InternalInvoice invoice) {
    return new InvoiceResponse(
        invoice.id(),
        invoice.number(),
        invoice.saleId(),
        invoice.documentType(),
        invoice.fiscalClassification(),
        invoice.status(),
        invoice.issuedAt(),
        invoice.saleDate(),
        new Party(invoice.company().id(), invoice.company().name()),
        new Party(invoice.client().id(), invoice.client().name()),
        invoice.lines().stream()
            .map(line ->
                new SaleLineResponse(
                    line.id(),
                    line.catalogItemId(),
                    line.itemName(),
                    line.itemDescription(),
                    line.unitPrice(),
                    line.quantity(),
                    line.subtotal(),
                    line.discount(),
                    line.taxRate(),
                    line.taxRate() == null ? null : line.taxAmount()))
            .toList(),
        invoice.total(),
        invoice.currency());
  }
}
