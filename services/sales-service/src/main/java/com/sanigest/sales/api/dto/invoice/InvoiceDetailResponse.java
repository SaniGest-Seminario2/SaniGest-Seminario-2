package com.sanigest.sales.api.dto.invoice;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.sanigest.sales.domain.enums.invoice.RequestedReceiptType;
import com.sanigest.sales.domain.enums.sale.SaleMode;
import com.sanigest.sales.domain.model.invoice.InternalInvoice.DocumentType;
import com.sanigest.sales.domain.model.invoice.InternalInvoice.FiscalClassification;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "Snapshot interno de factura con su saldo y estado de pago actuales.")
public record InvoiceDetailResponse(
    UUID id,
    String number,
    UUID saleId,
    DocumentType documentType,
    FiscalClassification fiscalClassification,
    @Schema(allowableValues = {"ISSUED", "CANCELLED"}) String status,
    Instant issuedAt,
    LocalDate saleDate,
    InvoiceResponse.Party company,
    InvoiceResponse.Party client,
    List<InvoiceLineResponse> lines,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal total,
    String currency,
    LocalDate issueDate,
    LocalDate dueDate,
    SaleMode mode,
    RequestedReceiptType requestedReceiptType,
    String notes,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal taxableBase,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal taxTotal,
    @Schema(allowableValues = {"ISSUED", "PARTIALLY_PAID", "PAID", "CANCELLED"})
        String paymentStatus,
    String outstandingBalance,
    String collectedAmount,
    Instant cancelledAt,
    UUID cancelledBy,
    String cancelReason) {}
