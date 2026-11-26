package com.sanigest.sales.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sanigest.sales.domain.enums.ReceivableOrigin;
import com.sanigest.sales.domain.enums.ReceivableStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ReceivableTest {
  private static final LocalDate DUE = LocalDate.of(2026, 12, 23);

  @Test
  void cancellationPreservesMoneyAndRemovesCollectibility() {
    var open = receivable("3500", "3500.000", ReceivableStatus.OPEN, DUE);
    assertThat(open.balance()).isEqualTo(new BigDecimal("3500.00"));
    assertThat(open.currency()).isEqualTo("DOP");
    assertThat(open.isOverdue(DUE)).isFalse();
    assertThat(open.isOverdue(DUE.plusDays(1))).isTrue();
    var cancelled = open.cancel();
    assertThat(cancelled.status()).isEqualTo(ReceivableStatus.CANCELLED);
    assertThat(cancelled.totalAmount()).isEqualTo(open.totalAmount());
    assertThat(cancelled.balance()).isEqualTo(open.balance());
    assertThat(cancelled.collectibleBalance()).isEqualTo(new BigDecimal("0.00"));
    assertThat(cancelled.isOverdue(DUE.plusDays(1))).isFalse();
    assertThat(cancelled.cancel()).isSameAs(cancelled);
  }

  @ParameterizedTest
  @CsvSource({
    "OPEN,10.00,10.00",
    "PARTIALLY_PAID,4.00,4.00",
    "PAID,0.00,0.00",
    "CANCELLED,10.00,0.00"
  })
  void projectsCollectibleBalanceWithoutChangingTheHistoricalBalance(
      ReceivableStatus status, String historical, String collectible) {
    var account = receivable("10.00", historical, status, DUE);
    assertThat(account.collectibleBalance()).isEqualTo(new BigDecimal(collectible));
    assertThat(account.balance()).isEqualTo(new BigDecimal(historical));
  }

  @ParameterizedTest
  @CsvSource({
    "0,0,OPEN",
    "-1,0,PAID",
    "10.001,10.001,OPEN",
    "10,11,OPEN",
    "10,8,OPEN",
    "10,10,PARTIALLY_PAID",
    "10,1,PAID",
    "10,-1,PARTIALLY_PAID"
  })
  void rejectsInvalidAmountsAndStates(String total, String balance, ReceivableStatus status) {
    assertThatThrownBy(() -> receivable(total, balance, status, DUE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsUnrepresentableDatesAndCancellationAfterPayment() {
    assertThatThrownBy(() -> receivable("10", "10", ReceivableStatus.OPEN, DUE.withYear(10000)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> receivable("10", "8", ReceivableStatus.PARTIALLY_PAID, DUE).cancel())
        .isInstanceOf(IllegalStateException.class);
  }

  private static Receivable receivable(
      String total, String balance, ReceivableStatus status, LocalDate due) {
    UUID sale = UUID.randomUUID();
    return new Receivable(
        sale,
        UUID.randomUUID(),
        sale,
        UUID.randomUUID(),
        "Cliente",
        new BigDecimal(total),
        new BigDecimal(balance),
        due,
        status,
        Instant.EPOCH,
        ReceivableOrigin.SALE);
  }
}
