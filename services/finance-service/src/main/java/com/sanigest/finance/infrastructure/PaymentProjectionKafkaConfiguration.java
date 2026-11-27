package com.sanigest.finance.infrastructure;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

@Configuration(proxyBeanMethods = false)
class PaymentProjectionKafkaConfiguration {
  static final String DLQ = "finance.cash-movements.dlq.v1";
  private static final Logger LOGGER =
      LoggerFactory.getLogger(PaymentProjectionKafkaConfiguration.class);

  @Bean
  DefaultErrorHandler paymentProjectionErrorHandler(KafkaTemplate<String, String> kafka) {
    var backoff = new ExponentialBackOffWithMaxRetries(3);
    backoff.setInitialInterval(1_000);
    backoff.setMultiplier(2);
    backoff.setMaxInterval(4_000);

    var handler =
        new DefaultErrorHandler((record, failure) -> quarantine(kafka, record, failure), backoff);
    handler.defaultFalse();
    handler.addRetryableExceptions(
        TransientDataAccessException.class,
        RecoverableDataAccessException.class,
        DataAccessResourceFailureException.class);
    handler.setResetStateOnExceptionChange(false);
    handler.setResetStateOnRecoveryFailure(false);
    handler.setRetryListeners((record, failure, attempt) -> {
      if (attempt == 3) {
        LOGGER.warn(
            "PAYMENT_PROJECTION_REPEATED_FAILURE topic={} partition={} offset={}",
            record.topic(),
            record.partition(),
            record.offset());
      }
    });
    return handler;
  }

  private void quarantine(
      KafkaTemplate<String, String> kafka, ConsumerRecord<?, ?> record, Exception failure) {
    // Store coordinates only. Operators can read the original from the restricted source topic.
    String key = "%s:%d:%d".formatted(record.topic(), record.partition(), record.offset());
    String reason =
        failure.getCause() instanceof IllegalArgumentException
            ? "INVALID_EVENT"
            : "PROJECTION_FAILED";
    String metadata =
        """
        {"topic":"%s","partition":%d,"offset":%d,"reason":"%s"}
        """
            .formatted(record.topic(), record.partition(), record.offset(), reason)
            .strip();
    try {
      kafka.send(DLQ, key, metadata).get(12, TimeUnit.SECONDS);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Payment DLQ publication interrupted", interrupted);
    } catch (ExecutionException | TimeoutException | RuntimeException publishFailure) {
      LOGGER.error("PAYMENT_PROJECTION_DLQ_UNAVAILABLE source={}", key);
      throw new IllegalStateException("Payment DLQ publication failed", publishFailure);
    }
    LOGGER.warn("PAYMENT_PROJECTION_DLQ source={}", key);
  }
}
