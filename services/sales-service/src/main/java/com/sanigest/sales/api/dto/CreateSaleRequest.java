package com.sanigest.sales.api.dto;

import com.sanigest.sales.application.dto.CreateSaleCommand;
import com.sanigest.sales.domain.enums.SaleMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateSaleRequest(
    @NotNull UUID clientId,
    @NotEmpty @Size(max = CreateSaleCommand.MAX_LINES) List<@NotNull @Valid CreateSaleLineRequest> lines,
    LocalDate saleDate,
    @NotNull SaleMode mode) {

  public CreateSaleCommand toCommand() {
    return new CreateSaleCommand(
        clientId, lines.stream().map(CreateSaleLineRequest::toCommand).toList(), saleDate, mode);
  }
}
