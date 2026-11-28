package com.sanigest.sales.domain.model.payment;

import com.sanigest.sales.domain.enums.payment.PaymentMethod;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class InvoicePayment {
  private InvoicePayment() {}

  public record Request(String clientName, BigDecimal balance, List<Line> lines, String notes) {
    public Request {
      clientName = normalize(clientName, 250);
      notes = normalize(notes, 500);
      balance = money(balance);
      if (clientName == null
          || lines == null
          || lines.isEmpty()
          || lines.size() > 100
          || lines.stream().anyMatch(java.util.Objects::isNull)) {
        throw new IllegalArgumentException("Invalid payment request");
      }
      lines = List.copyOf(lines);
    }

    public BigDecimal total() {
      return lines.stream().map(Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
  }

  public record Line(PaymentMethod method, BigDecimal amount, String reference) {
    public Line {
      amount = money(amount);
      reference = normalize(reference, 250);
      if (method == null || (method == PaymentMethod.CASH && reference != null)) {
        throw new IllegalArgumentException("Invalid payment method or reference");
      }
    }
  }

  public record Result(
      UUID paymentId,
      UUID invoiceId,
      UUID receivableId,
      UUID operatorId,
      UUID shiftId,
      Instant registeredAt,
      BigDecimal amount,
      BigDecimal balance,
      String currency,
      List<AppliedLine> lines,
      String notes) {}

  public record HistoryLine(
      UUID paymentId,
      UUID paymentLineId,
      UUID invoiceId,
      String clientName,
      PaymentMethod method,
      BigDecimal amount,
      BigDecimal balance) {}

  public record AppliedLine(UUID id, PaymentMethod method, BigDecimal amount, String reference) {}

  private static BigDecimal money(BigDecimal amount) {
    if (amount == null
        || amount.signum() <= 0
        || amount.compareTo(new BigDecimal("100000000000000000")) >= 0
        || amount.stripTrailingZeros().scale() > 2) {
      throw new IllegalArgumentException("Invalid payment amount");
    }
    return amount.setScale(2);
  }

  private static String normalize(String value, int maxLength) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String normalized = value.strip();
    if (normalized.length() > maxLength || normalized.indexOf(0) >= 0) {
      throw new IllegalArgumentException("Invalid payment text");
    }
    return normalized;
  }
}
