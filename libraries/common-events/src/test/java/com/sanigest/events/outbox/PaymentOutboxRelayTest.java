package com.sanigest.events.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class PaymentOutboxRelayTest {
  private static final String TOPIC = "sales.receivable-payment.v1";
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
  private final MockProducer<String, String> producer =
      new MockProducer<>(true, null, new StringSerializer(), new StringSerializer());
  private final PaymentOutboxRelay relay =
      new PaymentOutboxRelay(jdbc, manager, () -> producer, "sales.payment_outbox", TOPIC);
  private final UUID companyId = UUID.randomUUID();
  private final UUID eventId = UUID.randomUUID();

  PaymentOutboxRelayTest() {
    when(manager.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
  }

  @Test
  void marksPublishedOnlyAfterAcknowledgement() throws SQLException {
    pending(0);
    relay.publishPending();
    assertEquals(1, producer.history().size());
    var record = producer.history().getFirst();
    assertEquals(TOPIC, record.topic());
    assertEquals(companyId.toString(), record.key());
    assertEquals("payload", record.value());
    verify(jdbc).update(anyString(), eq(companyId), eq(eventId));
  }

  @Test
  void defersTheFirstFailure() throws SQLException {
    pending(0);
    producer.sendException = new TimeoutException();
    relay.publishPending();
    verify(jdbc).update(anyString(), eq(1), eq(2), eq(companyId), eq(eventId));
  }

  @Test
  void coolsDownAfterTheLastAttemptAndStartsANewCycle() throws SQLException {
    pending(4);
    producer.sendException = new TimeoutException();
    relay.publishPending();
    verify(jdbc).update(anyString(), eq(0), eq(300), eq(companyId), eq(eventId));
  }

  private void pending(int attempts) throws SQLException {
    var row = mock(ResultSet.class);
    when(row.getObject("event_id", UUID.class)).thenReturn(eventId);
    when(row.getObject("company_id", UUID.class)).thenReturn(companyId);
    when(row.getString("payload")).thenReturn("payload");
    when(row.getInt("attempts")).thenReturn(attempts);
    when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any()))
        .thenAnswer(call -> List.of(call.<RowMapper<Object>>getArgument(1).mapRow(row, 0)))
        .thenReturn(List.of());
  }
}
