package com.sanigest.sales.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.sanigest.sales.application.dto.CreateSaleCommand;
import com.sanigest.sales.application.exception.InvalidSaleRequest;
import com.sanigest.sales.domain.enums.RequestedReceiptType;
import com.sanigest.sales.domain.enums.SaleMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

public record CreateSaleRequest(
    @NotNull UUID clientId,
    @NotEmpty @Size(max = CreateSaleCommand.MAX_LINES) List<@NotNull @Valid CreateSaleLineRequest> lines,
    @JsonAlias("issueDate") LocalDate saleDate,
    @NotNull SaleMode mode,
    String dueDate,
    RequestedReceiptType requestedReceiptType,
    @Size(max = 1000) String notes) {
  public CreateSaleRequest(
      UUID clientId,
      List<CreateSaleLineRequest> lines,
      LocalDate saleDate,
      SaleMode mode,
      String dueDate) {
    this(clientId, lines, saleDate, mode, dueDate, null, null);
  }

  private static final Pattern ISO_DATE = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");

  public CreateSaleCommand toCommand() {
    return new CreateSaleCommand(
        clientId,
        lines.stream().map(CreateSaleLineRequest::toCommand).toList(),
        saleDate,
        mode,
        parseDueDate(),
        requestedReceiptType,
        notes);
  }

  private LocalDate parseDueDate() {
    if (dueDate == null) {
      return null;
    }
    if (!ISO_DATE.matcher(dueDate).matches()) {
      throw new InvalidSaleRequest();
    }
    try {
      return LocalDate.parse(dueDate);
    } catch (DateTimeParseException invalid) {
      throw new InvalidSaleRequest();
    }
  }
}
