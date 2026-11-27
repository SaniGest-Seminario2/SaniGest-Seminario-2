package com.sanigest.events.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FinancialWriteFingerprintTest {
  private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final LocalDate DATE = LocalDate.of(2026, 11, 27);

  @Test
  void canonicalizesPropertyOrderAndDecimalScaleButKeepsListOrder() {
    var request = new Request(new BigDecimal("10.00"), DATE, List.of("CASH", "TRANSFER"));
    var reordered = new Reordered(DATE, List.of("CASH", "TRANSFER"), new BigDecimal("10.0"));
    String fingerprint = FinancialWriteFingerprint.v1("payment", TARGET, request);

    assertTrue(fingerprint.matches("v1:[0-9a-f]{64}"));
    assertEquals(fingerprint, FinancialWriteFingerprint.v1("payment", TARGET, reordered));
    assertNotEquals(fingerprint, FinancialWriteFingerprint.v1("transfer", TARGET, request));
    assertNotEquals(
        fingerprint, FinancialWriteFingerprint.v1("payment", UUID.randomUUID(), request));
    assertNotEquals(
        fingerprint,
        FinancialWriteFingerprint.v1(
            "payment", TARGET, new Request(request.amount(), DATE, List.of("TRANSFER", "CASH"))));
  }

  private record Request(BigDecimal amount, LocalDate date, List<String> lines) {}

  private record Reordered(LocalDate date, List<String> lines, BigDecimal amount) {}
}
