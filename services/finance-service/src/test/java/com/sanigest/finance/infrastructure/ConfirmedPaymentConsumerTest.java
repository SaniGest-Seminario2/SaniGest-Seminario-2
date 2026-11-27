package com.sanigest.finance.infrastructure;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

class ConfirmedPaymentConsumerTest {
  private static final String COMPANY = "15fb9ef6-e011-4237-9b31-c69ed42a2bac";
  private static final String PAYABLE =
      """
      {"eventId":"c86b2c6f-9e00-4827-aaf8-dcbfb0862e5c","eventType":"PayablePaymentRegisteredV1",
       "version":1,"occurredAt":"2026-11-01T12:00:00Z","companyId":"15fb9ef6-e011-4237-9b31-c69ed42a2bac",
       "sourceId":"c86b2c6f-9e00-4827-aaf8-dcbfb0862e5c","traceId":"test",
       "paymentId":"c86b2c6f-9e00-4827-aaf8-dcbfb0862e5c","accountId":"c86b2c6f-9e00-4827-aaf8-dcbfb0862e5c",
       "method":"CASH","amount":"60.00","currency":"DOP"}
      """;

  private final ConfirmedPaymentConsumer consumer =
      new ConfirmedPaymentConsumer(JsonMapper.builder().build(), null);

  @Test
  void rejectsPaymentsFromTheWrongTopicOrCompanyBeforePersisting() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            consumer.project(
                new ConsumerRecord<>("sales.receivable-payment.v1", 0, 0, COMPANY, PAYABLE)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            consumer.project(
                new ConsumerRecord<>(
                    "expense.payable-payment.v1", 0, 0, "other-company", PAYABLE)));
  }

  @Test
  void persistsPayableAsCompanyOutflow() {
    var jdbc = mock(JdbcClient.class);
    var sql = mock(JdbcClient.StatementSpec.class, RETURNS_SELF);
    when(jdbc.sql(anyString())).thenReturn(sql);
    new ConfirmedPaymentConsumer(JsonMapper.builder().build(), jdbc)
        .project(new ConsumerRecord<>("expense.payable-payment.v1", 0, 0, COMPANY, PAYABLE));

    verify(sql).param("company", UUID.fromString(COMPANY));
    verify(sql).param("origin", "PAGO");
    verify(sql).param("direction", "OUTFLOW");
    verify(sql).update();
  }
}
