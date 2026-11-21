package com.sanigest.gateway;

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
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@Tag("smoke")
@Tag("regression")
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ServiceRoutingIT {
  private static final String ORIGIN = "https://frontend.example";
  private static final String COMPANY_ME = "/api/v1/companies/me";
  private static final HttpServer IDENTITY = backend("identity");
  private static final HttpServer COMPANY = backend("company");
  private static final HttpServer CATALOG = backend("catalog");

  @LocalServerPort private int port;
  private final HttpClient http = HttpClient.newHttpClient();

  @DynamicPropertySource
  static void destinations(DynamicPropertyRegistry registry) {
    registry.add("IDENTITY_SERVICE_URL", () -> origin(IDENTITY));
    registry.add("COMPANY_SERVICE_URL", () -> origin(COMPANY));
    registry.add("CATALOG_SERVICE_URL", () -> origin(CATALOG));
    registry.add("GATEWAY_CORS_ALLOWED_ORIGINS", () -> ORIGIN + ",https://another.example");
  }

  @AfterAll
  static void stopBackends() {
    IDENTITY.stop(0);
    COMPANY.stop(0);
    CATALOG.stop(0);
  }

  @Test
  void routesEveryPrefixToItsConfiguredBackend() throws Exception {
    for (var entry :
        Map.of(
                "/api/v1/auth/login", "identity",
                "/api/v1/users", "identity",
                "/api/v1/companies", "company",
                "/api/v1/clients", "catalog",
                "/api/v1/suppliers/123", "catalog",
                "/api/v1/catalog-items", "catalog")
            .entrySet()) {
      var response = send(request(gateway(), entry.getKey()).GET());
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.body()).isEqualTo(entry.getValue() + " GET " + entry.getKey() + " ");
    }
    assertThat(send(request(gateway(), "/api/v1/companies-extra").GET()).statusCode())
        .isEqualTo(404);
  }

  @Test
  void preservesMethodsRawQueryBodyHeadersAndBackendErrors() throws Exception {
    String path = "/api/v1/catalog-items/123?search=a%2Bb&tag=x&tag=y";
    String body = "{\"name\":\"Café\"}";
    for (String method : new String[] {"POST", "PUT", "PATCH", "DELETE"}) {
      var response =
          send(
              request(gateway(), path)
                  .header("Authorization", "Bearer synthetic")
                  .header("Content-Type", "application/json")
                  .header("X-Correlation-Id", "untrusted")
                  .header("traceparent", "untrusted")
                  .header("tracestate", "untrusted")
                  .header("baggage", "untrusted")
                  .method(method, HttpRequest.BodyPublishers.ofString(body)));
      assertThat(response.statusCode()).isEqualTo(422);
      assertThat(response.body()).isEqualTo("catalog " + method + " " + path + " " + body);
      assertThat(response.headers().firstValue("X-Backend-Authorization"))
          .contains("Bearer synthetic");
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
    for (String token : new String[] {"synthetic-valid", "synthetic-invalid"}) {
      var direct =
          send(
              request(origin(COMPANY), COMPANY_ME)
                  .header("Authorization", "Bearer " + token)
                  .GET());
      var proxied =
          send(request(gateway(), COMPANY_ME).header("Authorization", "Bearer " + token).GET());
      assertThat(proxied.statusCode())
          .isEqualTo(token.equals("synthetic-valid") ? 200 : 401)
          .isEqualTo(direct.statusCode());
      assertThat(proxied.body()).isEqualTo(direct.body());
      assertThat(proxied.headers().firstValue("WWW-Authenticate"))
          .isEqualTo(direct.headers().firstValue("WWW-Authenticate"));
    }
    assertThat(send(request(gateway(), COMPANY_ME).GET()).statusCode()).isEqualTo(401);
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
    assertThat(preflight(COMPANY_ME, ORIGIN, "GET", "X-Company-Id").statusCode()).isEqualTo(403);
  }

  @Test
  void returnsSafeProblemDetailsWhenTheUpstreamCloses() throws Exception {
    var response = send(request(gateway(), "/api/v1/auth/disconnected").GET());
    assertThat(response.statusCode()).isEqualTo(503);
    assertThat(response.headers().firstValue("Content-Type")).contains("application/problem+json");
    assertThat(response.body())
        .contains("UPSTREAM_UNAVAILABLE", "traceId")
        .doesNotContain("127.0.0.1");
  }

  @Test
  void returnsSafeProblemDetailsWhenTheUpstreamTimesOut() throws Exception {
    var response = send(request(gateway(), "/api/v1/catalog-items/slow").GET());
    assertThat(response.statusCode()).isEqualTo(504);
    assertThat(response.headers().firstValue("Content-Type")).contains("application/problem+json");
    assertThat(response.body()).contains("UPSTREAM_TIMEOUT", "traceId").doesNotContain("127.0.0.1");
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
      var headers = exchange.getRequestHeaders();
      var responseHeaders = exchange.getResponseHeaders();
      echoHeaders(exchange);
      String method = exchange.getRequestMethod();
      int status = method.equals("GET") ? 200 : 422;
      if (path.equals(COMPANY_ME)
          && !"Bearer synthetic-valid".equals(headers.getFirst("Authorization"))) {
        status = 401;
        responseHeaders.set("WWW-Authenticate", "Bearer");
      }
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
        new String[] {"Authorization", "Content-Type", "Traceparent", "Tracestate", "Baggage"}) {
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
