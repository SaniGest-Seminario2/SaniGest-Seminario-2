package com.sanigest.sales.api.dto;

import com.sanigest.sales.application.dto.CreateSaleCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateSaleLineRequest(
    @NotNull UUID catalogItemId,
    @NotNull @Positive BigDecimal quantity,
    @PositiveOrZero BigDecimal discount,
    @PositiveOrZero BigDecimal unitPrice,
    @PositiveOrZero BigDecimal taxRate) {
  public CreateSaleLineRequest(UUID catalogItemId, BigDecimal quantity, BigDecimal discount) {
    this(catalogItemId, quantity, discount, null, null);
  }

  public CreateSaleLineRequest(UUID catalogItemId, BigDecimal quantity) {
    this(catalogItemId, quantity, null);
  }

  CreateSaleCommand.Line toCommand() {
    return new CreateSaleCommand.Line(catalogItemId, quantity, discount, unitPrice, taxRate);
  }
}
