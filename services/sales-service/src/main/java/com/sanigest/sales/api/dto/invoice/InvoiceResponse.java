package com.sanigest.sales.api.dto.invoice;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.sanigest.sales.application.dto.sale.CreateSaleCommand;
import com.sanigest.sales.domain.enums.invoice.RequestedReceiptType;
import com.sanigest.sales.domain.enums.sale.SaleMode;
import com.sanigest.sales.domain.model.invoice.InternalInvoice;
import com.sanigest.sales.domain.model.invoice.InternalInvoice.DocumentType;
import com.sanigest.sales.domain.model.invoice.InternalInvoice.FiscalClassification;
import com.sanigest.sales.domain.model.invoice.InternalInvoice.Status;
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
    List<InvoiceLineResponse> lines,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal total,
    String currency,
    @JsonInclude(JsonInclude.Include.NON_NULL) LocalDate issueDate,
    @JsonInclude(JsonInclude.Include.NON_NULL) LocalDate dueDate,
    @JsonInclude(JsonInclude.Include.NON_NULL) SaleMode mode,
    @JsonInclude(JsonInclude.Include.NON_NULL) RequestedReceiptType requestedReceiptType,
    @JsonInclude(JsonInclude.Include.NON_NULL) String notes,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal taxableBase,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal taxTotal) {

  public record Party(UUID id, String name) {}

  public static InvoiceResponse from(InternalInvoice invoice) {
    return from(invoice, null);
  }

  public static InvoiceResponse from(InternalInvoice invoice, CreateSaleCommand form) {
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
        invoice.lines().stream().map(InvoiceLineResponse::from).toList(),
        invoice.total(),
        invoice.currency(),
        form == null ? null : invoice.saleDate(),
        form == null ? null : form.dueDate(),
        form == null ? null : form.mode(),
        form == null ? null : form.requestedReceiptType(),
        form == null ? null : form.notes(),
        invoice.lines().stream()
            .map(line -> line.subtotal())
            .reduce(BigDecimal.ZERO, BigDecimal::add),
        invoice.lines().stream()
            .map(line -> line.taxAmount())
            .reduce(BigDecimal.ZERO, BigDecimal::add));
  }
}
