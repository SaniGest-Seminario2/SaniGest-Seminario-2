package com.sanigest.finance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.sanigest.companyaccess.application.port.out.CompanyAccess;
import com.sanigest.companyaccess.application.port.out.CompanyAccess.MembershipContext;
import com.sanigest.companyaccess.application.port.out.CompanyAccess.MembershipRole;
import com.sanigest.companyaccess.application.port.out.CompanyAccess.Scope;
import com.sanigest.finance.application.dto.movement.ManualMovement.AccountBalance;
import com.sanigest.finance.application.dto.movement.ManualMovement.Category;
import com.sanigest.finance.application.dto.movement.ManualMovement.Document;
import com.sanigest.finance.application.dto.movement.ManualMovement.Method;
import com.sanigest.finance.application.dto.movement.ManualMovement.Request;
import com.sanigest.finance.application.dto.movement.ManualMovement.Type;
import com.sanigest.finance.application.exception.movement.InvalidManualMovement;
import com.sanigest.finance.application.exception.movement.ManualMovementConflict;
import com.sanigest.finance.application.port.in.movement.CashMovementQueries;
import com.sanigest.finance.application.port.in.movement.CashMovementQueries.Criteria;
import com.sanigest.finance.application.port.in.movement.CashMovementQueries.Origin;
import com.sanigest.finance.application.usecase.movement.ManualCashMovements;
import com.sanigest.finance.infrastructure.messaging.payment.ConfirmedPaymentConsumer;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"spring.kafka.listener.auto-startup=false", "sanigest.finance.admission-token="})
class FinanceServiceIT {
  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17.7-alpine")
          .withDatabaseName("sanigest_finance")
          .withUsername("sanigest_finance")
          .withPassword(UUID.randomUUID().toString());

  @MockitoBean JwtDecoder jwtDecoder;
  @MockitoBean CompanyAccess companyAccess;
  @Autowired ObjectMapper json;
  @LocalServerPort int port;
  @Autowired JdbcClient jdbc;
  @Autowired ManualCashMovements movements;
  @Autowired CashMovementQueries movementQueries;
  @Autowired ConfirmedPaymentConsumer paymentConsumer;

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Test
  void startupReportsReady() throws Exception {
    var request =
        HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/actuator/health/readiness"))
            .build();
    try (var client = HttpClient.newHttpClient()) {
      var response = client.send(request, HttpResponse.BodyHandlers.discarding());
      assertThat(response.statusCode()).isEqualTo(200);
    }
  }

  @Test
  void flywayCreatesServiceTablesInPostgres() {
    assertThat(
            jdbc.sql("select count(*) from finance.cash_shifts where company_id = :company")
                .param("company", UUID.randomUUID())
                .query(Long.class)
                .single())
        .isZero();
  }

  @Test
  void recordsAllTypesAndReplaysTransferWithoutDuplicatingItsEffects() {
    UUID company = UUID.randomUUID();
    UUID actor = UUID.randomUUID();
    var accounts = movements.options(company).movementAccounts();
    UUID cash = UUID.fromString(accounts.get(0).value());
    UUID bank = UUID.fromString(accounts.get(2).value());
    assertThat(cash)
        .isEqualTo(
            UUID.nameUUIDFromBytes(
                (company + ":finance:GENERAL_CASH").getBytes(StandardCharsets.UTF_8)));
    movements.register(company, actor, "income", movement(Type.INCOME, cash, null, "100"), null);
    movements.register(company, actor, "expense", movement(Type.EXPENSE, cash, null, "10"), null);
    var transfer = movement(Type.INTERNAL_TRANSFER, bank, cash, "30");
    var document =
        new Document(
            "application/pdf", "%PDF-test".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    var result = movements.register(company, actor, "transfer", transfer, document);
    assertThat(movements.register(company, actor, "transfer", transfer, document))
        .isEqualTo(result);
    assertThat(movements.document(company, result.id()).orElseThrow().content())
        .isEqualTo(document.content());
    assertThat(movements.document(UUID.randomUUID(), result.id())).isEmpty();
    assertThatThrownBy(() -> movements.register(company, actor, "transfer", transfer, null))
        .isInstanceOf(ManualMovementConflict.class);

    var balances = movements.balances(company);
    assertThat(balances.get(0).id()).isEqualTo(cash);
    assertThat(balances.get(0).balance()).isEqualByComparingTo("60");
    assertThat(balances.get(2).id()).isEqualTo(bank);
    assertThat(balances.get(2).balance()).isEqualByComparingTo("30");
    assertThat(movements.balances(UUID.randomUUID()))
        .extracting(AccountBalance::balance)
        .allSatisfy(balance -> assertThat(balance).isZero());
    assertThat(
            jdbc.sql("SELECT count(*) FROM finance.cash_movements WHERE manual_operation_id = :id")
                .param("id", result.id())
                .query(Integer.class)
                .single())
        .isEqualTo(2);
    UUID foreign =
        UUID.fromString(movements.options(UUID.randomUUID()).movementAccounts().getFirst().value());
    assertThatThrownBy(() ->
            movements.register(
                company, actor, "foreign", movement(Type.INCOME, foreign, null, "100"), null))
        .isInstanceOf(InvalidManualMovement.class);
    assertThatThrownBy(() ->
            movements.register(
                company, actor, "same", movement(Type.INTERNAL_TRANSFER, cash, cash, "30"), null))
        .isInstanceOf(InvalidManualMovement.class);
  }

  @Test
  void validatesEachRequiredFieldAndRejectsFilterValuesAndNonFinancialRoles() throws Exception {
    UUID company = UUID.randomUUID();
    UUID actor = UUID.randomUUID();
    when(jwtDecoder.decode("test-token"))
        .thenReturn(
            Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject(actor.toString())
                .expiresAt(Instant.now().plusSeconds(60))
                .build());
    when(companyAccess.membership(company, Scope.WRITE))
        .thenReturn(Optional.of(new MembershipContext(company, MembershipRole.OWNER, false)));
    UUID cash = UUID.fromString(movements.options(company).movementAccounts().getFirst().value());
    var valid = json.createObjectNode();
    valid
        .put("type", "INCOME")
        .put("date", "2026-11-28")
        .put("category", "OTHER_INCOME")
        .put("method", "CASH")
        .put("personOrEntity", "Entidad")
        .put("amount", 100)
        .put("observations", "Observación")
        .put("concept", "Concepto")
        .put("destinationAccountId", cash.toString())
        .put("bankReference", "REF");

    try (var client = HttpClient.newHttpClient()) {
      for (var property : valid.properties()) {
        var incomplete = valid.deepCopy();
        incomplete.remove(property.getKey());
        assertThat(post(client, company, incomplete.toString()).statusCode())
            .as("missing " + property.getKey())
            .isEqualTo(400);
      }
      assertThat(post(client, company, valid.deepCopy().put("amount", -1).toString()).statusCode())
          .isEqualTo(400);
      assertThat(post(client, company, valid.deepCopy().put("type", "ALL").toString()).statusCode())
          .isEqualTo(400);
      var created = post(client, company, valid.toString());
      assertThat(created.statusCode()).isEqualTo(201);
      assertThat(json.readTree(created.body()).get("actorId").asString())
          .isEqualTo(actor.toString());
      var viewer = Optional.of(new MembershipContext(company, MembershipRole.VIEWER, true));
      when(companyAccess.membership(company, Scope.READ)).thenReturn(viewer);
      when(companyAccess.membership(company, Scope.WRITE)).thenReturn(viewer);
      var options =
          HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/finance/options"))
              .header("Authorization", "Bearer test-token")
              .header("X-Company-Id", company.toString())
              .build();
      assertThat(client.send(options, HttpResponse.BodyHandlers.discarding()).statusCode())
          .isEqualTo(200);
      assertMovementReadContract(client, company);
      var selector = get(client, company, "/api/v1/accounts/options?method=CASH&search=general");
      assertThat(selector.statusCode()).isEqualTo(200);
      var selected = json.readTree(selector.body());
      assertThat(selected.size()).isEqualTo(1);
      assertThat(selected.get(0).get("id").asString()).isEqualTo(cash.toString());
      var balances = get(client, company, "/api/v1/financial-accounts");
      assertThat(balances.statusCode()).isEqualTo(200);
      assertThat(json.readTree(balances.body()).get(0).get("balance").asInt()).isEqualTo(100);
      assertThat(get(client, UUID.randomUUID(), "/api/v1/financial-accounts").statusCode())
          .isEqualTo(403);
      assertThat(post(client, company, valid.toString()).statusCode()).isEqualTo(403);

      when(companyAccess.membership(company, Scope.WRITE))
          .thenReturn(Optional.of(new MembershipContext(company, MembershipRole.OPERATOR, false)));
      assertThat(post(client, company, valid.toString()).statusCode()).isEqualTo(403);
      when(companyAccess.membership(company, Scope.WRITE))
          .thenReturn(Optional.of(new MembershipContext(company, MembershipRole.OPERATOR, true)));
      assertThat(post(client, company, valid.toString(), "operator-movement").statusCode())
          .isEqualTo(201);
      assertThat(post(client, company, valid.toString(), "operator-movement").statusCode())
          .isEqualTo(201);
      assertThat(
              jdbc.sql(
                      """
                      SELECT count(*) FROM finance.audit_entries
                      WHERE company_id = :company AND actor_id = :actor
                        AND resource_type = 'CASH_MOVEMENT' AND operation = 'INCOME'
                        AND result = 'SUCCESS'
                      """)
                  .param("company", company)
                  .param("actor", actor)
                  .query(Integer.class)
                  .single())
          .isEqualTo(2);

      assertAllMovementLimit(client, company, cash);
    }
  }

  private void assertAllMovementLimit(HttpClient client, UUID company, UUID cash) throws Exception {
    jdbc.sql(
            """
            INSERT INTO finance.cash_movements
              (event_id, company_id, event_type, source_id, payment_id, account_id,
               method, amount, currency, occurred_at, origin, direction)
            SELECT id, :company, 'CASH_SESSION', id, id, :account, 'CHECK', 1, 'DOP',
                   '2026-11-28T15:00:00Z', 'CAJA', 'INFLOW'
            FROM (SELECT gen_random_uuid() AS id FROM generate_series(1, 1001)) rows
            """)
        .param("company", company)
        .param("account", cash)
        .update();
    var cashFilter = "?origin=CASH_SESSION&method=CHECK";
    assertThat(get(client, company, "/api/v1/cash-movements/all" + cashFilter).statusCode())
        .isEqualTo(422);
    var cashPage = get(client, company, "/api/v1/cash-movements" + cashFilter);
    assertThat(json.readTree(cashPage.body()).get("totalElements").asInt()).isEqualTo(1001);
  }

  private void assertMovementReadContract(HttpClient client, UUID company) throws Exception {
    var list =
        get(
            client,
            company,
            "/api/v1/cash-movements?type=INCOME&origin=MANUAL&method=CASH&category=OTHER_INCOME");
    assertThat(list.statusCode()).isEqualTo(200);
    assertThat(json.readTree(list.body()).get("totalElements").asInt()).isEqualTo(1);
    var all = get(client, company, "/api/v1/cash-movements/all?search=ref");
    assertThat(all.statusCode()).isEqualTo(200);
    assertThat(json.readTree(all.body())).isEqualTo(json.readTree(list.body()).get("content"));
    var summary = get(client, company, "/api/v1/cash-movements/summary?from=2026-11-28");
    assertThat(summary.statusCode()).isEqualTo(200);
    assertThat(json.readTree(summary.body()).get("netFlow").asInt()).isEqualTo(100);
    assertThat(get(client, company, "/api/v1/cash-movements?size=101").statusCode()).isEqualTo(400);
    assertThat(get(client, company, "/api/v1/cash-movements?origin=unknown").statusCode())
        .isEqualTo(400);
    assertThat(get(client, UUID.randomUUID(), "/api/v1/cash-movements/all").statusCode())
        .isEqualTo(403);
  }

  @Test
  void uploadsAndDownloadsAnOptionalDocumentThroughHttp() throws Exception {
    UUID company = UUID.randomUUID();
    String token = "document-token";
    when(jwtDecoder.decode(token))
        .thenReturn(
            Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .subject(UUID.randomUUID().toString())
                .expiresAt(Instant.now().plusSeconds(60))
                .build());
    var owner = Optional.of(new MembershipContext(company, MembershipRole.OWNER, false));
    when(companyAccess.membership(company, Scope.WRITE)).thenReturn(owner);
    when(companyAccess.membership(company, Scope.READ)).thenReturn(owner);
    UUID account =
        UUID.fromString(movements.options(company).movementAccounts().getFirst().value());
    String body =
        "--test-boundary\r\nContent-Disposition: form-data; name=movement\r\n"
            + "Content-Type: application/json\r\n\r\n"
            + json.writeValueAsString(movement(Type.INCOME, account, null, "10"))
            + "\r\n--test-boundary\r\nContent-Disposition: form-data; name=document; filename=test.pdf\r\n"
            + "Content-Type: application/pdf\r\n\r\n%PDF-test\r\n--test-boundary--\r\n";
    try (var client = HttpClient.newHttpClient()) {
      var upload =
          HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/cash-movements"))
              .header("Authorization", "Bearer " + token)
              .header("X-Company-Id", company.toString())
              .header("Idempotency-Key", "upload")
              .header("Content-Type", "multipart/form-data; boundary=test-boundary")
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .build();
      var response = client.send(upload, HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).isEqualTo(201);
      String path = json.readTree(response.body()).get("documentUrl").asString();
      var download =
          HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
              .header("Authorization", "Bearer " + token)
              .header("X-Company-Id", company.toString())
              .build();
      var document = client.send(download, HttpResponse.BodyHandlers.ofString());
      assertThat(document.statusCode()).isEqualTo(200);
      assertThat(document.body()).isEqualTo("%PDF-test");
      assertThat(document.headers().firstValue("Content-Disposition")).contains("attachment");
    }
  }

  private HttpResponse<String> get(HttpClient client, UUID company, String path) throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Authorization", "Bearer test-token")
            .header("X-Company-Id", company.toString())
            .build();
    return client.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> post(HttpClient client, UUID company, String body) throws Exception {
    return post(client, company, body, "manual-movement");
  }

  private HttpResponse<String> post(HttpClient client, UUID company, String body, String key)
      throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/cash-movements"))
            .header("Authorization", "Bearer test-token")
            .header("X-Company-Id", company.toString())
            .header("Idempotency-Key", key)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    return client.send(request, HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void filtersAndSummarizesConfirmedMovementsAcrossPages() {
    UUID company = UUID.randomUUID();
    UUID actor = UUID.randomUUID();
    var accounts = movements.options(company).movementAccounts();
    UUID cash = UUID.fromString(accounts.get(0).value());
    UUID bank = UUID.fromString(accounts.get(2).value());
    UUID card = UUID.fromString(accounts.get(4).value());
    UUID payableAccount = UUID.fromString(accounts.get(3).value());

    movements.register(company, actor, "prior", movement(Type.INCOME, cash, null, "100"), null);
    var transfer =
        movements.register(
            company,
            actor,
            "transfer",
            movement(Type.INTERNAL_TRANSFER, bank, cash, "30"),
            new Document("application/pdf", new byte[] {1}));
    UUID shift = UUID.randomUUID();
    jdbc.sql(
            """
            INSERT INTO finance.cash_shifts (company_id, id, operator_id, opened_at)
            VALUES (:company, :shift, :actor, '2026-11-29T12:00:00Z')
            """)
        .param("company", company)
        .param("shift", shift)
        .param("actor", actor)
        .update();
    jdbc.sql("INSERT INTO finance.cash_accounts (company_id, id) VALUES (:company, :card)")
        .param("company", company)
        .param("card", card)
        .update();
    projectWithReplays(
        company,
        card,
        shift,
        "sales.receivable-payment.v1",
        "ReceivablePaymentRegisteredV1",
        "CREDIT_CARD",
        "50");
    projectWithReplays(
        company,
        payableAccount,
        null,
        "expense.payable-payment.v1",
        "PayablePaymentRegisteredV1",
        "TRANSFER",
        "20");

    LocalDate day = LocalDate.of(2026, 11, 29);
    var unfiltered = new Criteria("", null, null, null, null, null, day);
    var rows = movementQueries.all(company, unfiltered);
    var first = movementQueries.page(company, unfiltered, 0, 2);
    assertThat(first.totalElements()).isEqualTo(4);
    assertThat(first.content()).isEqualTo(rows.subList(0, 2));
    assertThat(movementQueries.page(company, unfiltered, 1, 2).content())
        .isEqualTo(rows.subList(2, 4));
    assertThat(movementQueries.page(company, unfiltered, 2, 2).totalElements()).isEqualTo(4);

    var transferOnly =
        new Criteria(
            "concepto",
            Type.INTERNAL_TRANSFER,
            Origin.MANUAL,
            Method.CASH,
            "OTHER_INCOME",
            null,
            null);
    assertThat(movementQueries.all(company, transferOnly))
        .singleElement()
        .satisfies(row -> {
          assertThat(row.id()).isEqualTo(transfer.id());
          assertThat(row.accountId()).isEqualTo(cash);
          assertThat(row.destinationAccountId()).isEqualTo(bank);
          assertThat(row.documentUrl()).endsWith("/" + transfer.id() + "/attachment");
        });
    var sales =
        new Criteria(
            "bank-ref", Type.INCOME, Origin.RECEIVABLE_PAYMENT, Method.CARD, "SALES", day, day);
    assertThat(movementQueries.all(company, sales))
        .singleElement()
        .satisfies(row -> {
          assertThat(row.referenceId()).isNotNull();
          assertThat(row.concept()).isNull();
          assertThat(row.destinationAccountId()).isNull();
          assertThat(row.receipt()).isNull();
          assertThat(row.documentUrl()).startsWith("/api/v1/invoices/");
        });
    assertThat(
            movementQueries.all(
                company,
                new Criteria(
                    "",
                    Type.EXPENSE,
                    Origin.PAYABLE_PAYMENT,
                    Method.TRANSFER,
                    "SUPPLIES",
                    day,
                    day)))
        .singleElement()
        .satisfies(row -> assertThat(row.accountId()).isEqualTo(payableAccount));
    assertMovementSummary(company, day, unfiltered);
    assertThat(movementQueries.all(UUID.randomUUID(), unfiltered)).isEmpty();
  }

  private void assertMovementSummary(UUID company, LocalDate day, Criteria unfiltered) {
    var summary =
        movementQueries.summary(company, new Criteria("", null, null, null, null, day, day));
    assertThat(summary.inflows()).isEqualByComparingTo("50");
    assertThat(summary.outflows()).isEqualByComparingTo("20");
    assertThat(summary.netFlow()).isEqualByComparingTo("30");
    assertThat(summary.openingBalance()).isEqualByComparingTo("100");
    assertThat(summary.closingBalance()).isEqualByComparingTo("130");
    assertThat(summary.movementCount()).isEqualTo(2);
    var allTime = movementQueries.summary(company, unfiltered);
    assertThat(allTime.inflows()).isEqualByComparingTo("150");
    assertThat(allTime.outflows()).isEqualByComparingTo("20");
    assertThat(allTime.movementCount()).isEqualTo(4);
  }

  private void projectWithReplays(
      UUID company,
      UUID account,
      UUID shift,
      String topic,
      String type,
      String method,
      String amount) {
    UUID event = UUID.randomUUID();
    String payload =
        """
        {"eventId":"%s","eventType":"%s","version":1,"occurredAt":"2026-11-29T12:05:00Z",
         "companyId":"%s","sourceId":"%s","traceId":"test","paymentId":"%s","accountId":"%s",
         "shiftId":%s,"method":"%s","amount":"%s","currency":"DOP","reference":"BANK-REF",
         "documentId":"%s"}
        """
            .formatted(
                event,
                type,
                company,
                UUID.randomUUID(),
                UUID.randomUUID(),
                account,
                json.writeValueAsString(shift),
                method,
                amount,
                UUID.randomUUID());
    paymentConsumer.project(new ConsumerRecord<>(topic, 0, 0, company.toString(), payload));
    paymentConsumer.project(new ConsumerRecord<>(topic, 0, 1, company.toString(), payload));
    paymentConsumer.project(
        new ConsumerRecord<>(
            topic,
            0,
            2,
            company.toString(),
            payload.replace(event.toString(), UUID.randomUUID().toString())));
  }

  private static Request movement(Type type, UUID destination, UUID source, String amount) {
    return new Request(
        type,
        LocalDate.of(2026, 11, 28),
        Category.OTHER_INCOME,
        Method.CASH,
        "Entidad",
        new BigDecimal(amount),
        "Observación",
        "Concepto",
        destination,
        source,
        "REF");
  }
}
