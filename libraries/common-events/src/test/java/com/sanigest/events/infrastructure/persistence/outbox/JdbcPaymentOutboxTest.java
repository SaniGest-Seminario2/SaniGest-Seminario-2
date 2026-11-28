package com.sanigest.events.infrastructure.persistence.outbox;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.sanigest.events.domain.enums.payment.PaymentEventTypeV1;
import com.sanigest.events.domain.enums.payment.PaymentMethodV1;
import com.sanigest.events.domain.model.payment.PaymentEventV1;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

class JdbcPaymentOutboxTest {
  private static final PaymentEventTypeV1 RECEIVABLE =
      PaymentEventTypeV1.ReceivablePaymentRegisteredV1;
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final JsonMapper json = JsonMapper.builder().build();
  private final JdbcPaymentOutbox outbox =
      new JdbcPaymentOutbox(jdbc, json, "sales.payment_outbox", RECEIVABLE.topic());

  @Test
  void storesTheCanonicalPayloadWithItsCompanyAndStableIdentifiers() {
    var event = event(RECEIVABLE);
    outbox.enqueue(event.companyId(), event);
    verify(jdbc)
        .update(
            anyString(),
            eq(event.eventId()),
            eq(event.companyId()),
            eq(event.sourceId()),
            isNull(),
            eq(json.writeValueAsString(event)));
  }

  @Test
  void rejectsAnEventForADifferentCompanyBeforeAccessingTheDatabase() {
    var event = event(RECEIVABLE);
    assertThrows(IllegalArgumentException.class, () -> outbox.enqueue(UUID.randomUUID(), event));
  }

  @Test
  void rejectsAnExpenseEventInSalesBeforeAccessingTheDatabase() {
    var event = event(PaymentEventTypeV1.PayablePaymentRegisteredV1);
    assertThrows(IllegalArgumentException.class, () -> outbox.enqueue(event.companyId(), event));
  }

  private static PaymentEventV1 event(PaymentEventTypeV1 type) {
    return new PaymentEventV1(
        UUID.randomUUID(),
        type,
        1,
        Instant.now(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        "trace",
        UUID.randomUUID(),
        null,
        UUID.randomUUID(),
        PaymentMethodV1.CASH,
        new BigDecimal("10.00"),
        "DOP",
        null,
        null,
        null,
        null);
  }
}
