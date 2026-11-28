package com.sanigest.sales.api.dto.invoice;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.sanigest.sales.api.dto.sale.CreateSaleRequest;
import com.sanigest.sales.application.dto.sale.CreateSaleCommand;
import com.sanigest.sales.domain.enums.invoice.RequestedReceiptType;
import com.sanigest.sales.domain.enums.sale.SaleMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateInvoiceRequest(
    @NotNull UUID clientId,
    @NotEmpty @Size(max = CreateSaleCommand.MAX_LINES) List<@NotNull @Valid InvoiceLine> lines,
    @Schema(description = "Fecha comercial. Puede enviarse como issueDate o saleDate.")
        @JsonAlias("issueDate")
        LocalDate saleDate,
    @NotNull SaleMode mode,
    String dueDate,
    RequestedReceiptType requestedReceiptType,
    @Size(max = 1000) String notes) {
  public CreateSaleCommand toCommand() {
    return new CreateSaleCommand(
        clientId,
        lines.stream().map(InvoiceLine::toCommand).toList(),
        saleDate,
        mode,
        CreateSaleRequest.parseDueDate(dueDate),
        requestedReceiptType,
        notes);
  }

  public record InvoiceLine(
      @NotNull UUID catalogItemId,
      @NotNull @Positive BigDecimal quantity,
      @NotNull @PositiveOrZero BigDecimal unitPrice,
      @Schema(description = "Descuento en DOP sobre el importe bruto.", defaultValue = "0")
          @PositiveOrZero BigDecimal discount,
      @Schema(
              description = "Fracción de ITBIS. Admite 0.18, 0.16 y 0 (exento).",
              defaultValue = "0")
          BigDecimal taxRate) {
    CreateSaleCommand.Line toCommand() {
      return new CreateSaleCommand.Line(catalogItemId, quantity, discount, unitPrice, taxRate);
    }
  }
}
