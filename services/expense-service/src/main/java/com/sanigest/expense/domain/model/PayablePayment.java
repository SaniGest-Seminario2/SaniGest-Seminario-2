package com.sanigest.expense.domain.model;

import com.sanigest.events.payment.PaymentMethodV1;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

public final class PayablePayment {
  public static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Santo_Domingo");

  private PayablePayment() {}

  public record Details(
      BigDecimal amount,
      PaymentMethodV1 method,
      UUID accountId,
      LocalDate date,
      String reference,
      String notes) {}

  public record Result(
      UUID paymentId,
      UUID payableId,
      UUID actorId,
      BigDecimal amount,
      PaymentMethodV1 method,
      UUID accountId,
      LocalDate date,
      String reference,
      String notes,
      Instant registeredAt,
      BigDecimal outstandingAmount,
      Status status) {}

  public enum Status {
    PARTIALLY_PAID,
    PAID
  }
}
