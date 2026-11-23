package com.sanigest.sales.api.dto;

import com.sanigest.sales.application.dto.CreateSaleCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateSaleLineRequest(
    @NotNull UUID catalogItemId, @NotNull @Positive BigDecimal quantity) {
  CreateSaleCommand.Line toCommand() {
    return new CreateSaleCommand.Line(catalogItemId, quantity);
  }
}
