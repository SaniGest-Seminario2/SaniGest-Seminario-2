package com.sanigest.events.outbox;

import com.sanigest.events.payment.PaymentEventTypeV1;
import java.util.Map;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
public class PaymentOutboxConfiguration {
  private final String table;
  private final String topic;

  PaymentOutboxConfiguration(@Value("${sanigest.payment-outbox.owner}") String owner) {
    this.topic =
        switch (owner) {
          case "sales" -> PaymentEventTypeV1.ReceivablePaymentRegisteredV1.topic();
          case "expense" -> PaymentEventTypeV1.PayablePaymentRegisteredV1.topic();
          default -> throw new IllegalArgumentException("Unsupported outbox owner: " + owner);
        };
    this.table = owner + ".payment_outbox";
  }

  @Bean
  PaymentOutbox paymentOutbox(JdbcTemplate jdbc, ObjectMapper json) {
    return new JdbcPaymentOutbox(jdbc, json, table, topic);
  }

  @Configuration(proxyBeanMethods = false)
  @EnableScheduling
  @ConditionalOnProperty(
      name = "sanigest.payment-outbox.relay-enabled",
      havingValue = "true",
      matchIfMissing = true)
  static class RelayConfiguration {
    @Bean(destroyMethod = "close")
    @Lazy
    Producer<String, String> paymentEventProducer(
        @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String brokers) {
      return new KafkaProducer<>(
          Map.of(
              ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
              brokers,
              ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
              StringSerializer.class,
              ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
              StringSerializer.class,
              ProducerConfig.ACKS_CONFIG,
              "all",
              ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG,
              true,
              ProducerConfig.MAX_BLOCK_MS_CONFIG,
              2000,
              ProducerConfig.LINGER_MS_CONFIG,
              0,
              ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG,
              3000,
              ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG,
              5000));
    }

    @Bean
    PaymentOutboxRelay paymentOutboxRelay(
        PaymentOutboxConfiguration config,
        JdbcTemplate jdbc,
        PlatformTransactionManager transactions,
        ObjectProvider<Producer<String, String>> paymentEventProducer) {
      return new PaymentOutboxRelay(
          jdbc, transactions, paymentEventProducer::getObject, config.table, config.topic);
    }
  }
}
