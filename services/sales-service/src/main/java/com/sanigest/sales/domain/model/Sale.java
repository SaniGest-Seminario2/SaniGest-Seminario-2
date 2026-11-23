package com.sanigest.sales.domain.model;

import com.sanigest.sales.domain.enums.SaleMode;
import com.sanigest.sales.domain.enums.SaleStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record Sale(
    UUID id,
    UUID companyId,
    UUID clientId,
    String clientName,
    LocalDate saleDate,
    SaleMode mode,
    SaleStatus status,
    BigDecimal total,
    List<SaleLine> lines,
    String code) {

  private static final Currency CURRENCY = Currency.getInstance("DOP");

  public static Sale register(
      UUID id,
      UUID companyId,
      CatalogCustomerSnapshot client,
      LocalDate date,
      SaleMode mode,
      List<SaleLine> lines) {
    var total = lines.stream().map(SaleLine::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    return new Sale(
        id,
        companyId,
        client.id(),
        client.name(),
        date,
        mode,
        SaleStatus.REGISTERED,
        total,
        lines,
        null);
  }

  public Sale withCode(String assignedCode) {
    Objects.requireNonNull(assignedCode, "code");
    return new Sale(
        id, companyId, clientId, clientName, saleDate, mode, status, total, lines, assignedCode);
  }

  public static LocalDate validateDate(LocalDate date) {
    if (date.getYear() < 1 || date.getYear() > 9999) {
      throw new IllegalArgumentException("saleDate must be in years 1 through 9999");
    }
    return date;
  }

  public Sale {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(companyId, "companyId");
    Objects.requireNonNull(clientId, "clientId");
    Objects.requireNonNull(saleDate, "saleDate");
    Objects.requireNonNull(mode, "mode");
    Objects.requireNonNull(status, "status");
    clientName = SnapshotText.name(clientName, 250, "clientName");
    validateDate(saleDate);
    total = SaleAmounts.amount(total, "total");
    lines = List.copyOf(lines);
    if (lines.isEmpty()) {
      throw new IllegalArgumentException("a sale must have at least one line");
    }
    Set<UUID> lineIds = new HashSet<>();
    BigDecimal sum = BigDecimal.ZERO;
    for (SaleLine line : lines) {
      if (!id.equals(line.saleId()) || !lineIds.add(line.id())) {
        throw new IllegalArgumentException("lines must belong to the sale and have distinct IDs");
      }
      sum = sum.add(line.subtotal());
    }
    if (total.compareTo(sum) != 0) {
      throw new IllegalArgumentException("total must equal the sum of line subtotals");
    }
  }

  public Currency currency() {
    return CURRENCY;
  }
}
