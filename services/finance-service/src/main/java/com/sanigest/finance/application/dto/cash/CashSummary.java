package com.sanigest.finance.application.dto.cash;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public final class CashSummary {
  private CashSummary() {}

  public record Amounts(
      BigDecimal totalCollections, BigDecimal netCash, Map<String, BigDecimal> otherMethods) {}

  public record Summary(
      UUID shiftId,
      BigDecimal totalCollections,
      long pendingInvoices,
      BigDecimal netCash,
      @Schema(
              description =
                  "Confirmed collections net of reversals for credit card, debit card, transfer and check.",
              example = "25.00",
              requiredMode = Schema.RequiredMode.REQUIRED)
          BigDecimal totalOtherMethods,
      Map<String, BigDecimal> otherMethods) {}
}
