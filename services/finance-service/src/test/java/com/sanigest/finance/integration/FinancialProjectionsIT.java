package com.sanigest.finance.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sanigest.finance.application.dto.reporting.FinancialSnapshots;
import com.sanigest.finance.application.exception.reporting.FinancialSourceFailure;
import com.sanigest.finance.application.exception.reporting.FinancialSourceFailure.Kind;
import com.sanigest.finance.application.usecase.reporting.FinancialProjections;
import com.sanigest.finance.infrastructure.integration.reporting.FinancialSources;
import com.sanigest.finance.infrastructure.messaging.payment.ConfirmedPaymentConsumer;
import com.sanigest.finance.infrastructure.persistence.reporting.PostgresFinancialProjections;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
class FinancialProjectionsIT {
  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17.7-alpine").withPassword(UUID.randomUUID().toString());

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static JdbcClient jdbc;

  @BeforeAll
  static void migrate() {
    var db =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    Flyway.configure().dataSource(db).load().migrate();
    jdbc = JdbcClient.create(db);
  }

  @Test
  void reconcilesSourceSnapshotsWithoutDuplicatingOrOverwritingNewerData() {
    var company = UUID.randomUUID();
    var otherCompany = UUID.randomUUID();
    var projections = new PostgresFinancialProjections(jdbc);
    var original = snapshots("2026-11-01T00:00:00Z", 100, 60);
    var corrected = snapshots("2026-11-02T00:00:00Z", 90, 50);

    projections.save(company, original);
    projections.save(otherCompany, original);
    projections.save(company, corrected);
    projections.save(company, corrected);
    projections.save(company, original);

    var rebuilt = JSON.readTree(projections.view(company));
    assertEquals(90, rebuilt.path("receivables").intValue());
    assertEquals(50, rebuilt.path("payables").intValue());
    assertEquals(90, rebuilt.path("salesSeries").get(0).path("amount").intValue());
    assertEquals(2, rebuilt.path("documentTotals").size());
    var isolated = JSON.readTree(projections.view(otherCompany));
    assertEquals(100, isolated.path("receivables").intValue());
    assertEquals(60, isolated.path("payables").intValue());
    assertTrue(JSON.readTree(projections.view(UUID.randomUUID())).path("partial").booleanValue());
  }

  @Test
  void rebuildRefreshesFromBothSourcesAndKeepsPriorDataWhenOneDeniesAccess() throws IOException {
    var company = UUID.randomUUID();
    var salesBody = new AtomicReference<>(snapshot("2026-11-01T00:00:00Z", "SALE", 100));
    var expenseBody = new AtomicReference<>(snapshot("2026-11-01T00:00:00Z", "PURCHASE", 60));
    var sales = source(company, salesBody);
    var expense = source(company, expenseBody);
    try {
      var store = new PostgresFinancialProjections(jdbc);
      var projections =
          new FinancialProjections(
              store,
              new FinancialSources(origin(sales), origin(expense), Duration.ofSeconds(2), null));
      var first = JSON.readTree(projections.rebuild(company, "token"));
      assertEquals(100, first.path("receivables").intValue());
      assertEquals(60, first.path("payables").intValue());

      salesBody.set(snapshot("2026-11-02T00:00:00Z", "SALE", 90));
      expenseBody.set(null);
      assertEquals(
          Kind.FORBIDDEN,
          assertThrows(FinancialSourceFailure.class, () -> projections.rebuild(company, "token"))
              .kind());
      assertEquals(100, JSON.readTree(store.view(company)).path("receivables").intValue());

      expenseBody.set(snapshot("2026-11-02T00:00:00Z", "PURCHASE", 50));
      projections.rebuild(company, "token");
      var reconciled = JSON.readTree(projections.read(company, "token"));
      assertEquals(90, reconciled.path("receivables").intValue());
      assertEquals(50, reconciled.path("payables").intValue());
    } finally {
      sales.stop(0);
      expense.stop(0);
    }
  }

  private static HttpServer source(UUID company, AtomicReference<String> snapshot)
      throws IOException {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/internal/financial-snapshot/v1",
        exchange -> {
          try (exchange) {
            String value = snapshot.get();
            var headers = exchange.getRequestHeaders();
            if (value == null
                || !company.toString().equals(headers.getFirst("X-Company-Id"))
                || !"Bearer token".equals(headers.getFirst("Authorization"))) {
              exchange.sendResponseHeaders(403, -1);
            } else if ("HEAD".equals(exchange.getRequestMethod())) {
              exchange.sendResponseHeaders(200, -1);
            } else {
              byte[] body = value.getBytes(StandardCharsets.UTF_8);
              exchange.getResponseHeaders().set("Content-Type", "application/json");
              exchange.sendResponseHeaders(200, body.length);
              exchange.getResponseBody().write(body);
            }
          }
        });
    server.start();
    return server;
  }

  private static String origin(HttpServer source) {
    return "http://127.0.0.1:" + source.getAddress().getPort();
  }

  @Test
  void confirmedPayableAndReversalAffectOnlyTheirCompanyAndAreIdempotent() {
    var company = UUID.randomUUID();
    var account = UUID.randomUUID();
    var payment = UUID.randomUUID();
    var event = UUID.randomUUID();
    var source = UUID.randomUUID();
    jdbc.sql("INSERT INTO finance.cash_accounts VALUES (:company, :account)")
        .param("company", company)
        .param("account", account)
        .update();
    var consumer = new ConfirmedPaymentConsumer(JSON, jdbc);
    var original = payable(company, account, payment, event, source, null, null);
    var reversal =
        payable(company, account, payment, UUID.randomUUID(), UUID.randomUUID(), event, source);
    var projections = new PostgresFinancialProjections(jdbc);

    consumer.project(original);
    consumer.project(original);
    consumer.project(payable(company, account, payment, UUID.randomUUID(), source, null, null));
    assertEquals(60, JSON.readTree(projections.view(company)).at("/cashTotals/outflow").intValue());
    consumer.project(reversal);
    consumer.project(reversal);
    var view = JSON.readTree(projections.view(company));
    assertEquals(0, view.at("/cashTotals/outflow").intValue());
    assertEquals(2, view.path("activity").size());
    assertEquals(0, JSON.readTree(projections.view(UUID.randomUUID())).path("activity").size());
  }

  private static ConsumerRecord<String, String> payable(
      UUID company,
      UUID account,
      UUID payment,
      UUID event,
      UUID source,
      UUID original,
      UUID originalSource) {
    boolean reversal = original != null;
    String body =
        """
        {"eventId":"%s","eventType":"%s","version":1,"occurredAt":"%s",
         "companyId":"%s","sourceId":"%s","traceId":"test","paymentId":"%s","accountId":"%s",
         "method":"CASH","amount":"60.00","currency":"DOP","originalEventId":%s,"originalSourceId":%s}
        """
            .formatted(
                event,
                reversal ? "PayablePaymentReversedV1" : "PayablePaymentRegisteredV1",
                reversal ? "2026-11-02T12:00:00Z" : "2026-11-01T12:00:00Z",
                company,
                source,
                payment,
                account,
                JSON.writeValueAsString(original),
                JSON.writeValueAsString(originalSource));
    return new ConsumerRecord<>("expense.payable-payment.v1", 0, 0, company.toString(), body);
  }

  private static FinancialSnapshots snapshots(String asOf, int sales, int expense) {
    return new FinancialSnapshots(
        snapshot(asOf, "SALE", sales), snapshot(asOf, "PURCHASE", expense));
  }

  private static String snapshot(String asOf, String kind, int amount) {
    return """
    {"dataAsOf":"%s","balance":%d,"series":[{"month":"2026-11-01","kind":"%s","amount":%d,"count":1}],
     "activity":[],"recentInvoices":[]}
    """
        .formatted(asOf, amount, kind, amount);
  }
}
