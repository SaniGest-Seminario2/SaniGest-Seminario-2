package com.sanigest.gateway.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@Tag("smoke")
@Tag("regression")
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ServiceRoutingIT {
  private static final String BACKEND_PROBLEM =
      """
      {"type":"about:blank","title":"Bad Request","status":400,"detail":"Backend failure",
      "errorCode":"BACKEND_CODE","traceId":"aabbccddeeff00112233445566778899","timestamp":"2026-11-26T00:00:00Z"}
      """;
  private static final String ORIGIN = "https://frontend.example";
  private static final String COMPANY_ME = "/api/v1/companies/me";
  private static final String TENANT = "e3fc1fd0-42ea-4a0d-9c14-6cbd44592b42";
  private static final HttpServer EXPENSE = backend("expense");
  private static final HttpServer INVENTORY = backend("inventory");
  private static final HttpServer FINANCE = backend("finance");
  private static final HttpServer SALES = backend("sales");
  private static final HttpServer IDENTITY = backend("identity");
  private static final HttpServer COMPANY = backend("company");
  private static final HttpServer CATALOG = backend("catalog");

  @LocalServerPort private int port;
  private final HttpClient http = HttpClient.newHttpClient();

  @DynamicPropertySource
  static void destinations(DynamicPropertyRegistry registry) {
    GatewayTestTokens.configure(registry);
    registry.add("IDENTITY_SERVICE_URL", () -> origin(IDENTITY));
    registry.add("COMPANY_SERVICE_URL", () -> origin(COMPANY));
    registry.add("CATALOG_SERVICE_URL", () -> origin(CATALOG));
    registry.add("INVENTORY_SERVICE_URL", () -> origin(INVENTORY));
    registry.add("EXPENSE_SERVICE_URL", () -> origin(EXPENSE));
    registry.add("SALES_SERVICE_URL", () -> origin(SALES));
    registry.add("FINANCE_SERVICE_URL", () -> origin(FINANCE));
    registry.add("GATEWAY_CORS_ALLOWED_ORIGINS", () -> ORIGIN + ",https://another.example");
  }

  @AfterAll
  static void stopBackends() {
    IDENTITY.stop(0);
    COMPANY.stop(0);
    CATALOG.stop(0);
    SALES.stop(0);
    FINANCE.stop(0);
    INVENTORY.stop(0);
    EXPENSE.stop(0);
  }

  @Test
  void routesEveryPrefixToItsConfiguredBackend() throws Exception {
    String token = GatewayTestTokens.validToken();
    for (var entry :
        Map.ofEntries(
                Map.entry("/api/v1/auth/login", "identity"),
                Map.entry("/api/v1/users", "identity"),
                Map.entry("/api/v1/companies", "company"),
                Map.entry("/api/v1/advisor/companies", "company"),
                Map.entry("/api/v1/companies/" + TENANT + "/options", "company"),
                Map.entry("/api/v1/catalog/options", "catalog"),
                Map.entry("/api/v1/clients/options", "catalog"),
                Map.entry("/api/v1/suppliers/options", "catalog"),
                Map.entry("/api/v1/catalog-items/options", "catalog"),
                Map.entry("/api/v1/inventory/options", "inventory"),
                Map.entry("/api/v1/warehouses/options", "inventory"),
                Map.entry("/api/v1/sales/options", "sales"),
                Map.entry("/api/v1/clients", "catalog"),
                Map.entry("/api/v1/clients/categories", "catalog"),
                Map.entry(
                    "/api/v1/clients/categories/955b9b1d-9c90-4404-a72c-ab1de57995dc", "catalog"),
                Map.entry("/api/v1/clients/reference-data/provinces", "catalog"),
                Map.entry("/api/v1/suppliers/123", "catalog"),
                Map.entry("/api/v1/catalog-items", "catalog"),
                Map.entry("/api/v1/catalog-items/units", "catalog"),
                Map.entry("/api/v1/catalog-items/tax-classifications", "catalog"),
                Map.entry("/api/v1/catalog-items/categories", "catalog"),
                Map.entry("/api/v1/catalog-items/categories/123", "catalog"),
                Map.entry("/api/v1/catalog-items/123/history", "catalog"),
                Map.entry("/api/v1/catalog-items/123/suppliers", "catalog"),
                Map.entry("/api/v1/suppliers/123/items", "catalog"),
                Map.entry("/api/v1/warehouses", "inventory"),
                Map.entry("/api/v1/warehouses/123", "inventory"),
                Map.entry("/api/v1/inventory/stock", "inventory"),
                Map.entry("/api/v1/inventory/stock/123/456", "inventory"),
                Map.entry("/api/v1/inventory/summary", "inventory"),
                Map.entry("/api/v1/inventory/movements", "inventory"),
                Map.entry("/api/v1/inventory/adjustments", "inventory"),
                Map.entry("/api/v1/sales", "sales"),
                Map.entry("/api/v1/receivables/123", "sales"))
            .entrySet()) {
      var response =
          send(
              request(gateway(), entry.getKey())
                  .header("Authorization", "Bearer " + token)
                  .header("X-Company-Id", TENANT)
                  .GET());
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.body()).isEqualTo(entry.getValue() + " GET " + entry.getKey() + " ");
    }
    assertThat(
            send(request(gateway(), "/api/v1/companies-extra")
                    .header("Authorization", "Bearer " + token)
                    .GET())
                .statusCode())
        .isEqualTo(404);
  }

  @Test
  void routesActiveCashSummaryOnlyWithCompanyContext() throws Exception {
    String path = "/api/v1/cash-sessions/active/summary";
    String token = "Bearer " + GatewayTestTokens.validToken();
    var missingCompany = send(request(gateway(), path).header("Authorization", token).GET());
    assertThat(missingCompany.statusCode()).isEqualTo(400);
    var summary =
        send(
            request(gateway(), path)
                .header("Authorization", token)
                .header("X-Company-Id", TENANT)
                .GET());
    assertThat(summary.statusCode()).isEqualTo(200);
    assertThat(summary.body()).isEqualTo("finance GET " + path + " ");
  }

  @Test
  void forwardsInvoiceCreationToSales() throws Exception {
    var response =
        send(
            request(gateway(), "/api/v1/invoices")
                .header("Authorization", "Bearer " + GatewayTestTokens.validToken())
                .header("X-Company-Id", TENANT)
                .header("Idempotency-Key", "direct-invoice")
                .POST(HttpRequest.BodyPublishers.ofString("{}")));
    assertThat(response.statusCode()).isEqualTo(422);
    assertThat(response.body()).isEqualTo("sales POST /api/v1/invoices {}");
  }

  @Test
  void forwardsInvoicePaymentToSales() throws Exception {
    String path = "/api/v1/invoices/123/payments";
    var response =
        send(
            request(gateway(), path)
                .header("Authorization", "Bearer " + GatewayTestTokens.validToken())
                .header("X-Company-Id", TENANT)
                .header("Idempotency-Key", "payment")
                .POST(HttpRequest.BodyPublishers.ofString("{}")));
    assertThat(response.statusCode()).isEqualTo(422);
    assertThat(response.body()).isEqualTo("sales POST " + path + " {}");
  }

  @Test
  void writesGatewayProblemsAndPreservesBackendProblems() throws Exception {
    var client = WebTestClient.bindToServer().baseUrl(gateway()).build();
    client
        .get()
        .uri(COMPANY_ME)
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .valueEquals("WWW-Authenticate", "Bearer")
        .expectHeader()
        .contentType("application/problem+json")
        .expectBody()
        .jsonPath("$.errorCode")
        .isEqualTo("AUTHENTICATION_REQUIRED")
        .jsonPath("$.traceId")
        .isNotEmpty();
    String token = "Bearer " + GatewayTestTokens.validToken();
    client
        .get()
        .uri("/api/v1/catalog-items")
        .header("Authorization", token)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType("application/problem+json")
        .expectBody()
        .jsonPath("$.errorCode")
        .isEqualTo("COMPANY_CONTEXT_REQUIRED")
        .jsonPath("$.errors['X-Company-Id']")
        .isNotEmpty();
    client
        .get()
        .uri("/api/v1/catalog-items/empty-error")
        .header("Authorization", token)
        .header("X-Company-Id", TENANT)
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentType("application/problem+json")
        .expectBody()
        .jsonPath("$.errorCode")
        .isEqualTo("RESOURCE_NOT_FOUND");
    client
        .get()
        .uri("/api/v1/catalog-items/backend-problem")
        .header("Authorization", token)
        .header("X-Company-Id", TENANT)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType("application/problem+json")
        .expectBody(String.class)
        .isEqualTo(BACKEND_PROBLEM);
  }

  @Test
  void inventoryRequiresOneCompanySelectorAndStripsUntrustedContext() throws Exception {
    String token = "Bearer " + GatewayTestTokens.validToken();
    var path = "/api/v1/inventory/stock";
    assertThat(send(request(gateway(), path).header("Authorization", token).GET()).body())
        .contains("COMPANY_CONTEXT_REQUIRED");
    assertThat(
            send(request(gateway(), path)
                    .header("Authorization", token)
                    .header("X-Company-Id", TENANT)
                    .header("X-Company-Id", TENANT)
                    .GET())
                .body())
        .contains("INVALID_COMPANY_CONTEXT");
    var forwarded =
        send(
            request(gateway(), path)
                .header("Authorization", token)
                .header("X-Company-Id", TENANT)
                .header("X-Company-User-Assertion", "untrusted")
                .GET());
    assertThat(forwarded.statusCode()).isEqualTo(200);
    assertThat(forwarded.headers().firstValue("X-Backend-X-Company-User-Assertion")).isEmpty();
  }

  @Test
  void routesPayableReadsWithCompanyAndPreservesQueries() throws Exception {
    String token = "Bearer " + GatewayTestTokens.validToken();
    for (String path :
        List.of(
            "/api/v1/payables?status=PENDING&page=1",
            "/api/v1/payables/all?search=paper",
            "/api/v1/payables/supplier-balances?supplierIds=" + TENANT)) {
      assertThat(send(request(gateway(), path).GET()).statusCode()).isEqualTo(401);
      assertThat(send(request(gateway(), path).header("Authorization", token).GET()).statusCode())
          .isEqualTo(400);
      var response =
          send(
              request(gateway(), path)
                  .header("Authorization", token)
                  .header("X-Company-Id", TENANT)
                  .GET());
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.body()).isEqualTo("expense GET " + path + " ");
      assertThat(response.headers().firstValue("X-Backend-X-Company-Id")).contains(TENANT);
    }
  }

  @Test
  void routesExpenseRegistrationWithCompanyAndIdempotencyKey() throws Exception {
    String path = "/api/v1/expenses";
    String token = "Bearer " + GatewayTestTokens.validToken();
    var body = HttpRequest.BodyPublishers.ofString("{}");

    var missingCompany = send(request(gateway(), path).header("Authorization", token).POST(body));
    assertThat(missingCompany.statusCode()).isEqualTo(400);
    assertThat(missingCompany.body()).contains("COMPANY_CONTEXT_REQUIRED");

    var forwarded =
        send(
            request(gateway(), path)
                .header("Authorization", token)
                .header("X-Company-Id", TENANT)
                .header("Idempotency-Key", "registration-1")
                .header("X-Company-Role", "OWNER")
                .POST(body));
    assertThat(forwarded.statusCode()).isEqualTo(422);
    assertThat(forwarded.body()).isEqualTo("expense POST " + path + " {}");
    assertThat(forwarded.headers().firstValue("X-Backend-X-Company-Id")).contains(TENANT);
    assertThat(forwarded.headers().firstValue("X-Backend-Idempotency-Key"))
        .contains("registration-1");
    assertThat(forwarded.headers().firstValue("X-Backend-X-Company-Role")).isEmpty();

    var get =
        send(
            request(gateway(), path)
                .header("Authorization", token)
                .header("X-Company-Id", TENANT)
                .GET());
    assertThat(get.statusCode()).isEqualTo(404);
  }

  @Test
  void routesInvitationAcceptanceToCompanyOnlyForAuthenticatedRequests() throws Exception {
    String path = "/api/v1/invitations/sample-invitation/accept";
    assertThat(
            send(request(gateway(), path).POST(HttpRequest.BodyPublishers.noBody())).statusCode())
        .isEqualTo(401);

    var response =
        send(
            request(gateway(), path)
                .header("Authorization", "Bearer " + GatewayTestTokens.validToken())
                .POST(HttpRequest.BodyPublishers.noBody()));
    assertThat(response.statusCode()).isEqualTo(422);
    assertThat(response.body()).isEqualTo("company POST " + path + " ");
  }

  @Test
  void preservesMethodsRawQueryBodyHeadersAndBackendErrors() throws Exception {
    String path = "/api/v1/catalog-items/123?search=a%2Bb&tag=x&tag=y";
    String body = "{\"name\":\"Café\"}";
    String authorization = "Bearer " + GatewayTestTokens.validToken();
    for (String method : new String[] {"POST", "PUT", "PATCH", "DELETE"}) {
      var response =
          send(
              request(gateway(), path)
                  .header("Authorization", authorization)
                  .header("Content-Type", "application/json")
                  .header("X-Company-Id", TENANT)
                  .header("X-Correlation-Id", "untrusted")
                  .header("traceparent", "untrusted")
                  .header("tracestate", "untrusted")
                  .header("baggage", "untrusted")
                  .method(method, HttpRequest.BodyPublishers.ofString(body)));
      assertThat(response.statusCode()).isEqualTo(422);
      assertThat(response.body()).isEqualTo("catalog " + method + " " + path + " " + body);
      assertThat(response.headers().firstValue("X-Backend-Authorization")).contains(authorization);
      assertThat(response.headers().firstValue("X-Backend-Content-Type"))
          .contains("application/json");
      String correlation = response.headers().firstValue("X-Correlation-Id").orElseThrow();
      assertThat(response.headers().firstValue("X-Backend-Correlation")).contains(correlation);
      assertThat(response.headers().firstValue("X-Backend-Traceparent").orElseThrow())
          .matches("00-" + correlation.replace("-", "") + "-[a-f0-9]{16}-00");
      assertThat(response.headers().firstValue("X-Backend-Trace-Id"))
          .contains(correlation.replace("-", ""));
      assertThat(response.headers().firstValue("X-Backend-Tracestate")).isEmpty();
      assertThat(response.headers().firstValue("X-Backend-Baggage")).isEmpty();
    }
  }

  @Test
  void preservesCompanyMeResponsesFromTheProtectedBackend() throws Exception {
    String authorization = "Bearer " + GatewayTestTokens.validToken();
    var direct =
        send(request(origin(COMPANY), COMPANY_ME).header("Authorization", authorization).GET());
    var proxied = send(request(gateway(), COMPANY_ME).header("Authorization", authorization).GET());
    assertThat(proxied.statusCode()).isEqualTo(200).isEqualTo(direct.statusCode());
    assertThat(proxied.body()).isEqualTo(direct.body());
    assertThat(send(request(gateway(), COMPANY_ME).GET()).statusCode()).isEqualTo(401);
    var forged =
        send(
            request(gateway(), COMPANY_ME)
                .header("Authorization", "Bearer invalid")
                .header("X-User-Id", "forged")
                .GET());
    assertThat(forged.statusCode()).isEqualTo(401);
    assertThat(forged.headers().firstValue("X-Backend-Authorization")).isEmpty();
  }

  @Test
  void routesExplicitPublicAuthCommandsWithoutAnAccessToken() throws Exception {
    for (String operation : new String[] {"login", "register", "refresh", "logout"}) {
      String path = "/api/v1/auth/" + operation;
      var response = send(request(gateway(), path).POST(HttpRequest.BodyPublishers.noBody()));
      assertThat(response.statusCode()).isEqualTo(422);
      assertThat(response.body()).isEqualTo("identity POST " + path + " ");
      assertThat(send(request(gateway(), path).GET()).statusCode()).isEqualTo(401);
    }
  }

  @Test
  void enforcesCorsForRequestsAndPreflight() throws Exception {
    for (String allowed : new String[] {ORIGIN, "https://another.example"}) {
      var preflight =
          preflight(
              "/api/v1/clients", allowed, "PATCH", "Authorization, Content-Type, Idempotency-Key");
      assertThat(preflight.statusCode()).isEqualTo(200);
      assertThat(preflight.headers().firstValue("Access-Control-Allow-Origin")).contains(allowed);
      assertThat(preflight.headers().firstValue("Access-Control-Allow-Credentials")).isEmpty();
    }
    var actual = send(request(gateway(), COMPANY_ME).header("Origin", ORIGIN).GET());
    assertThat(actual.statusCode()).isEqualTo(401);
    assertThat(actual.headers().firstValue("Access-Control-Allow-Origin")).contains(ORIGIN);
    assertThat(actual.headers().firstValue("Access-Control-Expose-Headers"))
        .contains("X-Correlation-Id");
    assertThat(
            send(request(gateway(), COMPANY_ME).header("Origin", "https://denied.example").GET())
                .statusCode())
        .isEqualTo(403);
    assertThat(preflight(COMPANY_ME, "https://denied.example", "GET", "Authorization").statusCode())
        .isEqualTo(403);
    assertThat(preflight(COMPANY_ME, ORIGIN, "TRACE", "Authorization").statusCode()).isEqualTo(403);
    assertThat(preflight(COMPANY_ME, ORIGIN, "GET", "X-Company-Id").statusCode()).isEqualTo(200);
  }

  @Test
  void returnsSafeProblemDetailsWhenTheUpstreamCloses() throws Exception {
    var response =
        send(
            request(gateway(), "/api/v1/auth/disconnected")
                .header("Authorization", "Bearer " + GatewayTestTokens.validToken())
                .GET());
    assertThat(response.statusCode()).isEqualTo(503);
    assertThat(response.headers().firstValue("Content-Type")).contains("application/problem+json");
    assertThat(response.body())
        .contains("UPSTREAM_UNAVAILABLE", "traceId")
        .doesNotContain("127.0.0.1");
  }

  @Test
  void returnsSafeProblemDetailsWhenTheUpstreamTimesOut() throws Exception {
    var response =
        send(
            request(gateway(), "/api/v1/catalog-items/slow")
                .header("X-Company-Id", TENANT)
                .header("Authorization", "Bearer " + GatewayTestTokens.validToken())
                .GET());
    assertThat(response.statusCode()).isEqualTo(504);
    assertThat(response.headers().firstValue("Content-Type")).contains("application/problem+json");
    assertThat(response.body()).contains("UPSTREAM_TIMEOUT", "traceId").doesNotContain("127.0.0.1");
  }

  @Test
  void rejectsMissingDuplicateAndConflictingSelectorsBeforeRouting() throws Exception {
    String token = "Bearer " + GatewayTestTokens.validToken();
    var sales = request(gateway(), "/api/v1/sales").header("Authorization", token).GET();
    assertThat(send(sales).body()).contains("COMPANY_CONTEXT_REQUIRED");
    assertThat(send(sales.header("X-Company-Id", "1-1-1-1-1")).body())
        .contains("INVALID_COMPANY_CONTEXT");
    assertThat(send(sales.header("X-Company-Id", TENANT)).body())
        .contains("INVALID_COMPANY_CONTEXT");
    var company =
        request(gateway(), "/api/v1/companies/" + TENANT + "/users")
            .header("Authorization", token)
            .GET();
    assertThat(send(company).statusCode()).isEqualTo(200);
    assertThat(send(company.header("X-Company-Id", "0098a4d2-cf3e-4599-a9da-af9972e50251")).body())
        .contains("COMPANY_CONTEXT_MISMATCH");
    assertThat(
            send(request(gateway(), "/internal/v2/company-access")
                    .header("Authorization", token)
                    .GET())
                .statusCode())
        .isEqualTo(404);
  }

  @Test
  void forwardsOnlyThePublicSelectorAndStripsInternalHeaders() throws Exception {
    String authorization = "Bearer " + GatewayTestTokens.validToken();
    var response =
        send(
            request(gateway(), "/api/v1/clients")
                .header("Authorization", authorization)
                .header("X-Company-Id", TENANT.toUpperCase(java.util.Locale.ROOT))
                .header("X-Company-User-Assertion", "spoofed")
                .header("X-Company-Role", "OWNER")
                .header("X-User-Id", "spoofed")
                .GET());
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("X-Backend-X-Company-Id")).contains(TENANT);
    assertThat(response.headers().firstValue("X-Backend-Authorization")).contains(authorization);
    for (String header : new String[] {"X-Company-User-Assertion", "X-Company-Role", "X-User-Id"}) {
      assertThat(response.headers().firstValue("X-Backend-" + header)).isEmpty();
    }
  }

  private HttpResponse<String> preflight(String path, String origin, String method, String headers)
      throws Exception {
    return send(
        request(gateway(), path)
            .header("Origin", origin)
            .header("Access-Control-Request-Method", method)
            .header("Access-Control-Request-Headers", headers)
            .method("OPTIONS", HttpRequest.BodyPublishers.noBody()));
  }

  private String gateway() {
    return "http://127.0.0.1:" + port;
  }

  private static String origin(HttpServer server) {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  private HttpRequest.Builder request(String origin, String path) {
    return HttpRequest.newBuilder(URI.create(origin + path)).timeout(Duration.ofSeconds(5));
  }

  private HttpResponse<String> send(HttpRequest.Builder request)
      throws IOException, InterruptedException {
    return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private static HttpServer backend(String name) {
    try {
      var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.setExecutor(task -> Thread.startVirtualThread(task));
      server.createContext("/", exchange -> respond(name, exchange));
      server.start();
      return server;
    } catch (IOException e) {
      throw new IllegalStateException("Cannot start " + name + " test backend", e);
    }
  }

  private static void respond(String name, HttpExchange exchange) throws IOException {
    try (exchange) {
      String path = exchange.getRequestURI().getPath();
      if (path.endsWith("/disconnected")) {
        return;
      }
      if (path.endsWith("/slow")) {
        try {
          Thread.sleep(Duration.ofSeconds(3));
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return;
        }
      }
      if (path.endsWith("/empty-error")) {
        exchange.sendResponseHeaders(404, -1);
        return;
      }
      if (path.endsWith("/backend-problem")) {
        byte[] problem = BACKEND_PROBLEM.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/problem+json");
        exchange.sendResponseHeaders(400, problem.length);
        exchange.getResponseBody().write(problem);
        return;
      }
      echoHeaders(exchange);
      String method = exchange.getRequestMethod();
      int status = method.equals("GET") ? 200 : 422;
      String requestBody =
          new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      byte[] body =
          (name + " " + method + " " + exchange.getRequestURI() + " " + requestBody)
              .getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(status, body.length);
      exchange.getResponseBody().write(body);
    }
  }

  private static void echoHeaders(HttpExchange exchange) {
    var headers = exchange.getRequestHeaders();
    var responseHeaders = exchange.getResponseHeaders();
    for (String header :
        new String[] {
          "Authorization",
          "Content-Type",
          "Traceparent",
          "Tracestate",
          "Baggage",
          "X-Company-Id",
          "X-Company-User-Assertion",
          "X-Company-Role",
          "Idempotency-Key",
          "X-User-Id"
        }) {
      if (headers.containsKey(header)) {
        responseHeaders.set("X-Backend-" + header, headers.getFirst(header));
      }
    }
    if (headers.containsKey("X-Correlation-Id")) {
      responseHeaders.set("X-Backend-Correlation", headers.getFirst("X-Correlation-Id"));
      responseHeaders.set("X-Backend-Trace-Id", headers.getFirst("X-Trace-Id"));
    }
  }
}
