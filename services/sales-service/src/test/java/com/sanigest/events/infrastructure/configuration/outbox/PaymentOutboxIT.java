package com.sanigest.events.infrastructure.configuration.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sanigest.events.application.port.out.persistence.PaymentOutbox;
import com.sanigest.events.domain.enums.payment.PaymentEventTypeV1;
import com.sanigest.events.domain.enums.payment.PaymentMethodV1;
import com.sanigest.events.domain.model.payment.PaymentEventV1;
import com.sanigest.events.infrastructure.messaging.outbox.PaymentOutboxRelay;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
@Tag("regression")
class PaymentOutboxIT {
  private static final String TABLE = "sales.payment_outbox";
  private static final PaymentEventTypeV1 REGISTERED =
      PaymentEventTypeV1.ReceivablePaymentRegisteredV1;
  private static final PaymentEventTypeV1 REVERSED = PaymentEventTypeV1.ReceivablePaymentReversedV1;
  private static final String TOPIC = REGISTERED.topic();

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17.7-alpine")
          .withUsername("sanigest_sales")
          .withPassword(UUID.randomUUID().toString());

  @Container static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.1");

  @Test
  void recoversCommittedPaymentAfterKafkaOutageAndPublishesTheReversalAfterIt() throws Exception {
    DataSource dataSource =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    Flyway.configure().dataSource(dataSource).load().migrate();
    try (var context = context(dataSource);
        var admin = Admin.create(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()));
        var producer =
            new PaymentOutboxConfiguration.RelayConfiguration()
                .paymentEventProducer(KAFKA.getBootstrapServers());
        var consumer =
            new KafkaConsumer<String, String>(
                Map.of(
                    "bootstrap.servers",
                    KAFKA.getBootstrapServers(),
                    "group.id",
                    UUID.randomUUID().toString(),
                    "auto.offset.reset",
                    "earliest"),
                new StringDeserializer(),
                new StringDeserializer())) {
      admin.createTopics(List.of(new NewTopic(TOPIC, 1, (short) 1))).all().get();
      consumer.subscribe(List.of(TOPIC));

      var outbox = context.getBean(PaymentOutbox.class);
      var jdbc = context.getBean(JdbcTemplate.class);
      var mapper = context.getBean(JsonMapper.class);
      var manager = context.getBean(JdbcTransactionManager.class);
      var transaction = new TransactionTemplate(manager);
      var event = paymentLine(null);
      var reversal = paymentLine(event);
      assertThatThrownBy(() -> outbox.enqueue(event.companyId(), event))
          .isInstanceOf(IllegalTransactionStateException.class);
      transaction.executeWithoutResult(status -> {
        outbox.enqueue(event.companyId(), event);
        status.setRollbackOnly();
      });
      assertThat(jdbc.queryForObject("SELECT count(*) FROM " + TABLE, Integer.class)).isZero();

      var relay = new PaymentOutboxRelay(jdbc, manager, () -> producer, TABLE, TOPIC);
      var docker = DockerClientFactory.instance().client();
      docker.pauseContainerCmd(KAFKA.getContainerId()).exec();
      try {
        transaction.executeWithoutResult(status -> outbox.enqueue(event.companyId(), event));
        relay.publishPending();
        assertThat(jdbc.queryForObject("SELECT attempts FROM " + TABLE, Integer.class)).isOne();
        assertThat(publishedCount(jdbc)).isZero();
      } finally {
        docker.unpauseContainerCmd(KAFKA.getContainerId()).exec();
      }

      jdbc.update("UPDATE " + TABLE + " SET available_at = clock_timestamp()");
      // A reversal can commit while another relay has claimed the original.
      try (var claim = dataSource.getConnection()) {
        claim.setAutoCommit(false);
        try (var statement =
            claim.prepareStatement(
                "SELECT event_id FROM " + TABLE + " WHERE event_id = ? FOR NO KEY UPDATE")) {
          statement.setObject(1, event.eventId());
          statement.executeQuery().close();
          transaction.executeWithoutResult(status -> {
            jdbc.execute("SET LOCAL lock_timeout = '1s'");
            outbox.enqueue(reversal.companyId(), reversal);
          });
          relay.publishPending();
          assertThat(publishedCount(jdbc)).isZero();
        } finally {
          claim.rollback();
        }
      }

      relay.publishPending();
      assertThat(publishedCount(jdbc)).isEqualTo(2);
      relay.publishPending();

      var received = new ArrayList<PaymentEventV1>();
      var deadline = Instant.now().plusSeconds(15);
      while (received.size() < 2 && Instant.now().isBefore(deadline)) {
        for (var record : consumer.poll(Duration.ofMillis(250))) {
          assertThat(record.key()).isEqualTo(event.companyId().toString());
          received.add(mapper.readValue(record.value(), PaymentEventV1.class));
        }
      }
      assertThat(received).containsExactly(event, reversal);
      assertThat(consumer.poll(Duration.ofMillis(500))).isEmpty();
    }
  }

  private static int publishedCount(JdbcTemplate jdbc) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM " + TABLE + " WHERE published_at IS NOT NULL", Integer.class);
  }

  private static AnnotationConfigApplicationContext context(DataSource dataSource) {
    var context = new AnnotationConfigApplicationContext();
    context
        .getEnvironment()
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "outboxTest",
                Map.of(
                    "sanigest.payment-outbox.owner",
                    "sales",
                    "sanigest.payment-outbox.relay-enabled",
                    "false")));
    context.registerBean(JdbcTemplate.class, () -> new JdbcTemplate(dataSource));
    context.registerBean(
        JdbcTransactionManager.class, () -> new JdbcTransactionManager(dataSource));
    context.registerBean(JsonMapper.class, () -> JsonMapper.builder().build());
    context.register(PaymentOutboxConfiguration.class, TransactionConfiguration.class);
    context.refresh();
    return context;
  }

  private static PaymentEventV1 paymentLine(PaymentEventV1 original) {
    return new PaymentEventV1(
        UUID.randomUUID(),
        original == null ? REGISTERED : REVERSED,
        1,
        Instant.now(),
        original == null ? UUID.randomUUID() : original.companyId(),
        UUID.randomUUID(),
        "test",
        original == null ? UUID.randomUUID() : original.paymentId(),
        null,
        original == null ? UUID.randomUUID() : original.accountId(),
        PaymentMethodV1.CASH,
        new BigDecimal("50.00"),
        "DOP",
        original == null ? null : original.eventId(),
        original == null ? null : original.sourceId(),
        null,
        null,
        null,
        null);
  }

  @EnableTransactionManagement
  static class TransactionConfiguration {}
}
