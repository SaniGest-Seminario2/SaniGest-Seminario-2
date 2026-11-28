package com.sanigest.expense.infrastructure.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sanigest.events.payment.PaymentMethodV1;
import com.sanigest.expense.application.exception.ExpenseFailure;
import com.sanigest.expense.application.port.out.integration.InventoryReceipts.Entry;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class ExpenseReferencesTest {
  private final UUID company = UUID.randomUUID();
  private final UUID id = UUID.randomUUID();
  private final AtomicReference<Response> response = new AtomicReference<>();
  private final AtomicReference<String> received = new AtomicReference<>();
  private HttpServer server;
  private URI origin;

  @BeforeEach
  void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          try (exchange) {
            received.set(
                String.join(
                    "|",
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().toString(),
                    String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")),
                    String.valueOf(exchange.getRequestHeaders().getFirst("X-Company-Id")),
                    String.valueOf(exchange.getRequestHeaders().getFirst("Idempotency-Key"))));
            exchange.getRequestBody().readAllBytes();
            var answer = response.get();
            byte[] body = answer.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(answer.status(), body.length == 0 ? -1 : body.length);
            exchange.getResponseBody().write(body);
          }
        });
    server.start();
    origin = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    var jwt =
        Jwt.withTokenValue("user-token").header("alg", "RS256").subject(id.toString()).build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
  }

  @AfterEach
  void stop() {
    SecurityContextHolder.clearContext();
    server.stop(0);
  }

  @Test
  void resolvesReferencesWithCompanyAndBearerAndRecordsReceiptIdempotently() {
    var references = new ExpenseReferences(origin, origin);
    answer(
        200,
        """
        {"id":"%s","name":"Supplier","status":"ACTIVE","paymentTerms":"BOTH","creditDays":30}
        """
            .formatted(id));
    assertTrue(references.findSupplier(company, id).orElseThrow().active());
    assertEquals(
        "GET|/api/v1/suppliers/" + id + "|Bearer user-token|" + company + "|null", received.get());

    answer(
        200,
        """
        {"id":"%s","name":"Item","active":true,"eligibleForPurchase":true,"type":"PRODUCT","unitDivisible":true}
        """
            .formatted(id));
    assertTrue(references.findItem(company, id).orElseThrow().divisible());
    answer(200, "{\"id\":\"" + id + "\",\"status\":\"ACTIVE\"}");
    assertTrue(references.findWarehouse(company, id).orElseThrow().active());
    answer(200, "{\"trackingEnabled\":true}");
    assertTrue(references.tracksStock(company, id, id));
    answer(201, "{\"id\":\"" + id + "\"}");
    assertEquals(id, references.recordEntry(new Entry(company, id, id, id, id, BigDecimal.ONE)));
    assertEquals(
        "POST|/internal/v1/inventory/purchase-receipts|Bearer user-token|" + company + "|" + id,
        received.get());
  }

  @Test
  void distinguishesUnknownReferencesDeniedAccessAndUnavailableServices() {
    var references = new ExpenseReferences(origin, origin);
    answer(404, "");
    assertTrue(references.findSupplier(company, id).isEmpty());
    assertEquals(
        "EXPENSE_INVALID_STOCK_REFERENCE",
        assertThrows(ExpenseFailure.class, () -> references.tracksStock(company, id, id))
            .getMessage());
    answer(403, "");
    assertEquals(
        ExpenseFailure.Kind.FORBIDDEN,
        assertThrows(ExpenseFailure.class, () -> references.findItem(company, id)).kind());
    answer(503, "");
    assertEquals(
        ExpenseFailure.Kind.UNAVAILABLE,
        assertThrows(ExpenseFailure.class, () -> references.findWarehouse(company, id)).kind());
    assertEquals(
        "EXPENSE_INVENTORY_RECEIPT_PENDING",
        assertThrows(
                ExpenseFailure.class,
                () -> references.recordEntry(new Entry(company, id, id, id, id, BigDecimal.ONE)))
            .getMessage());
  }

  @Test
  void checksPaymentEligibilityUsingServiceCredentials() {
    var accounts = new FinancePaymentAccounts(origin, "service-token");
    answer(204, "");
    accounts.requireEligible(company, id, PaymentMethodV1.TRANSFER);
    assertEquals(
        "GET|/internal/companies/"
            + company
            + "/payment-accounts/"
            + id
            + "?method=TRANSFER|Bearer service-token|null|null",
        received.get());
    answer(404, "");
    assertEquals(
        ExpenseFailure.Kind.INVALID,
        assertThrows(
                ExpenseFailure.class,
                () -> accounts.requireEligible(company, id, PaymentMethodV1.CASH))
            .kind());
    answer(503, "");
    assertEquals(
        ExpenseFailure.Kind.UNAVAILABLE,
        assertThrows(
                ExpenseFailure.class,
                () -> accounts.requireEligible(company, id, PaymentMethodV1.CASH))
            .kind());
    assertEquals(
        ExpenseFailure.Kind.UNAVAILABLE,
        assertThrows(
                ExpenseFailure.class,
                () ->
                    new FinancePaymentAccounts(origin, "")
                        .requireEligible(company, id, PaymentMethodV1.CASH))
            .kind());
  }

  private void answer(int status, String body) {
    response.set(new Response(status, body));
  }

  private record Response(int status, String body) {}
}
