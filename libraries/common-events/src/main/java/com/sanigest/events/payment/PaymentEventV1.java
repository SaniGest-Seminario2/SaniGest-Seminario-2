package com.sanigest.events.payment;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentEventV1(
    UUID eventId,
    PaymentEventTypeV1 eventType,
    int version,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant occurredAt,
    UUID companyId,
    UUID sourceId,
    String traceId,
    UUID paymentId,
    UUID shiftId,
    UUID accountId,
    PaymentMethodV1 method,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
    String currency,
    UUID originalEventId,
    UUID originalSourceId,
    UUID documentId,
    UUID actorId) {

  public static final int VERSION = 1;

  public PaymentEventV1 {
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(eventType, "eventType");
    Objects.requireNonNull(occurredAt, "occurredAt");
    Objects.requireNonNull(companyId, "companyId");
    Objects.requireNonNull(sourceId, "sourceId");
    Objects.requireNonNull(paymentId, "paymentId");
    Objects.requireNonNull(accountId, "accountId");
    Objects.requireNonNull(method, "method");
    Currency.getInstance(Objects.requireNonNull(currency, "currency"));

    if (version != VERSION) {
      throw new IllegalArgumentException("Unsupported payment event version: " + version);
    }
    if (traceId == null || traceId.isBlank()) {
      throw new IllegalArgumentException("traceId is required");
    }
    if (amount == null || amount.signum() <= 0) {
      throw new IllegalArgumentException("amount must be positive");
    }
    if (eventType.isReversal()) {
      validateReversal(eventId, sourceId, originalEventId, originalSourceId);
    } else if (originalEventId != null || originalSourceId != null) {
      throw new IllegalArgumentException("Original identifiers are only allowed on reversals");
    }
  }

  private static void validateReversal(
      UUID eventId, UUID sourceId, UUID originalEventId, UUID originalSourceId) {
    Objects.requireNonNull(originalEventId, "originalEventId");
    Objects.requireNonNull(originalSourceId, "originalSourceId");
    if (eventId.equals(originalEventId) || sourceId.equals(originalSourceId)) {
      throw new IllegalArgumentException("A reversal requires its own eventId and sourceId");
    }
  }
}
