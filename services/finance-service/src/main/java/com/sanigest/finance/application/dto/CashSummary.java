package com.sanigest.finance.application.dto;

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
      Map<String, BigDecimal> otherMethods) {}
}
