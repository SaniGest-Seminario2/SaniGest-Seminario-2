package com.sanigest.finance.infrastructure.messaging.payment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ListenerExecutionFailedException;

class PaymentProjectionKafkaConfigurationTest {
  private static final String TOPIC = "sales.receivable-payment.v1";
  private static final String DLQ_KEY = TOPIC + ":0:12";
  private static final String METADATA =
      """
      {"topic":"sales.receivable-payment.v1","partition":0,"offset":12,"reason":"INVALID_EVENT"}
      """
          .strip();

  private final ConsumerRecord<String, String> record =
      new ConsumerRecord<>(TOPIC, 0, 12, "original-key", "original-payload");
  private final Exception invalid =
      new ListenerExecutionFailedException("Listener failed", new IllegalArgumentException());

  @Test
  void quarantinesInvalidInputImmediatelyUsingOnlyDiagnosticMetadata() {
    var kafka = kafkaTemplate();
    when(kafka.send(PaymentProjectionKafkaConfiguration.DLQ, DLQ_KEY, METADATA))
        .thenReturn(CompletableFuture.completedFuture(null));
    var handler = new PaymentProjectionKafkaConfiguration().paymentProjectionErrorHandler(kafka);

    assertTrue(handler.handleOne(invalid, record, null, null));
    verify(kafka).send(PaymentProjectionKafkaConfiguration.DLQ, DLQ_KEY, METADATA);
  }

  @Test
  void keepsOffsetUnrecoveredWhenQuarantinePublicationFails() {
    var kafka = kafkaTemplate();
    when(kafka.send(PaymentProjectionKafkaConfiguration.DLQ, DLQ_KEY, METADATA))
        .thenReturn(
            CompletableFuture.failedFuture(new IllegalStateException("Broker unavailable")));
    var handler = new PaymentProjectionKafkaConfiguration().paymentProjectionErrorHandler(kafka);

    assertFalse(handler.handleOne(invalid, record, null, null));
  }

  @Test
  void quarantinesTransientFailuresAfterThreeRetries() {
    var kafka = kafkaTemplate();
    when(kafka.send(
            PaymentProjectionKafkaConfiguration.DLQ,
            DLQ_KEY,
            METADATA.replace("INVALID_EVENT", "PROJECTION_FAILED")))
        .thenReturn(CompletableFuture.completedFuture(null));
    var handler = new PaymentProjectionKafkaConfiguration().paymentProjectionErrorHandler(kafka);
    var unavailable =
        new ListenerExecutionFailedException(
            "Listener failed", new TransientDataAccessResourceException("Database unavailable"));

    for (int attempt = 0; attempt < 3; attempt++) {
      assertFalse(handler.handleOne(unavailable, record, null, null));
    }
    assertTrue(handler.handleOne(unavailable, record, null, null));
  }

  @SuppressWarnings("unchecked")
  private KafkaTemplate<String, String> kafkaTemplate() {
    return mock(KafkaTemplate.class);
  }
}
