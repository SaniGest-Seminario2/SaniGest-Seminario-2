package com.sanigest.events.outbox;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class PaymentOutboxRelay {
  private static final Logger LOGGER = LoggerFactory.getLogger(PaymentOutboxRelay.class);
  private static final int BATCH_SIZE = 20;
  private static final int MAX_ATTEMPTS_PER_CYCLE = 5;
  private static final int COOLDOWN_SECONDS = 300;

  private final JdbcTemplate jdbc;
  private final TransactionTemplate transactions;
  private final Supplier<Producer<String, String>> producer;
  private final String table;
  private final String topic;

  PaymentOutboxRelay(
      JdbcTemplate jdbc,
      PlatformTransactionManager transactions,
      Supplier<Producer<String, String>> producer,
      String table,
      String topic) {
    this.jdbc = jdbc;
    this.transactions = new TransactionTemplate(transactions);
    this.producer = producer;
    this.table = table;
    this.topic = topic;
  }

  @Scheduled(fixedDelayString = "${sanigest.payment-outbox.poll-delay:1000}")
  public void publishPending() {
    for (int processed = 0; processed < BATCH_SIZE; processed++) {
      if (!Boolean.TRUE.equals(transactions.execute(status -> publishNext()))) {
        return;
      }
    }
  }

  private boolean publishNext() {
    var pending =
        jdbc.query(
            """
            SELECT pending.event_id, pending.company_id, pending.payload, pending.attempts
            FROM %1$s pending
            WHERE pending.published_at IS NULL AND pending.available_at <= statement_timestamp()
              AND (pending.original_event_id IS NULL OR EXISTS (
                  SELECT 1 FROM %1$s original
                  WHERE original.company_id = pending.company_id
                    AND original.event_id = pending.original_event_id
                    AND original.published_at IS NOT NULL
              ))
            ORDER BY pending.available_at, pending.created_at, pending.event_id
            LIMIT 1 FOR NO KEY UPDATE OF pending SKIP LOCKED
            """
                .formatted(table),
            (rs, row) ->
                new Pending(
                    rs.getObject("event_id", UUID.class),
                    rs.getObject("company_id", UUID.class),
                    rs.getString("payload"),
                    rs.getInt("attempts")));
    if (pending.isEmpty()) {
      return false;
    }

    var event = pending.getFirst();
    try {
      producer
          .get()
          .send(new ProducerRecord<>(topic, event.companyId().toString(), event.payload()))
          .get(6, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Payment outbox relay interrupted", e);
    } catch (BeanCreationException | KafkaException | ExecutionException | TimeoutException e) {
      defer(event, e);
      return false;
    }
    jdbc.update(
        "UPDATE %s SET published_at = clock_timestamp() WHERE company_id = ? AND event_id = ?"
            .formatted(table),
        event.companyId(),
        event.eventId());
    return true;
  }

  private void defer(Pending event, Exception failure) {
    int attempts = event.attempts() + 1;
    boolean exhausted = attempts == MAX_ATTEMPTS_PER_CYCLE;
    int delaySeconds = exhausted ? COOLDOWN_SECONDS : 1 << attempts;
    jdbc.update(
        """
        UPDATE %s
        SET attempts = ?, available_at = clock_timestamp() + ? * interval '1 second'
        WHERE company_id = ? AND event_id = ?
        """
            .formatted(table),
        exhausted ? 0 : attempts,
        delaySeconds,
        event.companyId(),
        event.eventId());
    LOGGER.warn(
        "Payment event {} on {} deferred for {}s ({})",
        event.eventId(),
        topic,
        delaySeconds,
        failure.getClass().getSimpleName());
  }

  private record Pending(UUID eventId, UUID companyId, String payload, int attempts) {}
}
