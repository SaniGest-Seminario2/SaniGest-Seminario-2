package com.sanigest.sales.domain.model.sale;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

public record SaleCancellation(UUID cancelledBy, Instant cancelledAt, String cancelReason) {
  public static final int MAX_REASON_LENGTH = 500;

  public SaleCancellation {
    Objects.requireNonNull(cancelledBy, "cancelledBy");
    cancelledAt = Objects.requireNonNull(cancelledAt, "cancelledAt").truncatedTo(ChronoUnit.MICROS);
    cancelReason = normalizeReason(cancelReason);
  }

  private static String normalizeReason(String reason) {
    if (reason == null) {
      throw new IllegalArgumentException("cancellation reason is required");
    }
    String normalized = reason.strip();
    if (normalized.isEmpty()
        || normalized.length() > MAX_REASON_LENGTH
        || normalized.indexOf(0) >= 0) {
      throw new IllegalArgumentException("invalid cancellation reason");
    }
    return normalized;
  }
}
