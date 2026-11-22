package com.sanigest.gateway.integration;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.UUID;
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
class ApiGatewayIT {
  private static final String PING = "/api/v1/gateway/ping";
  private static final String ORIGIN = "http://localhost:3000";
  private static final String CORRELATION = "X-Correlation-Id";
  private static final HttpServer BACKEND = backend();

  @LocalServerPort private int port;
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  @DynamicPropertySource
  static void backendUrl(DynamicPropertyRegistry registry) {
    GatewayTestTokens.configure(registry);
    registry.add(
        "GATEWAY_TEST_BACKEND_URL", () -> "http://127.0.0.1:" + BACKEND.getAddress().getPort());
  }

  @AfterAll
  static void stopBackend() {
    BACKEND.stop(0);
  }

  @Test
  void routesRequestsWithGeneratedCorrelationAndWithoutCredentials() throws Exception {
    var response =
        send(
            request(PING)
                .header(CORRELATION, "untrusted")
                .header("Authorization", "Bearer " + GatewayTestTokens.validToken())
                .header("Cookie", "session=secret")
                .GET());
    assertThat(response.statusCode()).isEqualTo(200);
    var id = response.headers().firstValue(CORRELATION).orElseThrow();
    assertThat(UUID.fromString(id).toString()).isEqualTo(id);
    assertThat(response.body()).isEqualTo("GET /ping " + id + " null null");
    assertThat(response.headers().allValues(CORRELATION)).containsExactly(id);
    assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
  }

  @Test
  void exposesSanitizedHealthAndReturnsSafeNotFound() throws Exception {
    for (String path :
        List.of("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness")) {
      var response = send(request(path).GET());
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.body())
          .contains("\"status\":\"UP\"")
          .doesNotContain("\"details\"", "\"components\"");
    }
    String token = GatewayTestTokens.validToken();
    for (String path :
        List.of("/actuator/env", "/actuator/gateway/routes", "/api/v1/catalog-items", "/unknown")) {
      var response = send(request(path).header("Authorization", "Bearer " + token).GET());
      assertThat(response.statusCode()).isEqualTo(404);
      assertThat(response.body())
          .doesNotContain("exception", "stacktrace", "diagnostic-ping", "127.0.0.1");
    }
  }

  @Test
  void acceptsOnlyAllowedCorsOriginMethodAndHeaders() throws Exception {
    var allowed = preflight(ORIGIN, "GET", "Accept");
    assertThat(allowed.statusCode()).isBetween(200, 299);
    assertThat(allowed.headers().firstValue("Access-Control-Allow-Origin")).contains(ORIGIN);
    assertThat(allowed.headers().firstValue("Access-Control-Allow-Credentials")).isEmpty();
    var forbidden = preflight("https://untrusted.example", "GET", "Accept");
    assertThat(forbidden.statusCode()).isEqualTo(403);
    assertThat(forbidden.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
    assertThat(preflight(ORIGIN, "POST", "Accept").statusCode()).isEqualTo(403);
    assertThat(preflight(ORIGIN, "GET", "Authorization").statusCode()).isEqualTo(403);
  }

  private HttpResponse<String> preflight(String origin, String method, String header)
      throws Exception {
    return send(
        request(PING)
            .header("Origin", origin)
            .header("Access-Control-Request-Method", method)
            .header("Access-Control-Request-Headers", header)
            .method("OPTIONS", HttpRequest.BodyPublishers.noBody()));
  }

  private HttpRequest.Builder request(String path) {
    return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
        .timeout(Duration.ofSeconds(5));
  }

  private HttpResponse<String> send(HttpRequest.Builder request)
      throws IOException, InterruptedException {
    return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private static HttpServer backend() {
    try {
      var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext(
          "/ping",
          exchange -> {
            try (exchange) {
              var headers = exchange.getRequestHeaders();
              var body =
                  ("%s %s %s %s %s"
                          .formatted(
                              exchange.getRequestMethod(),
                              exchange.getRequestURI(),
                              headers.getFirst(CORRELATION),
                              headers.getFirst("Authorization"),
                              headers.getFirst("Cookie")))
                      .getBytes(StandardCharsets.UTF_8);
              exchange.getResponseHeaders().set(CORRELATION, "backend-supplied");
              exchange.getResponseHeaders().set("Set-Cookie", "session=backend");
              exchange.sendResponseHeaders(200, body.length);
              exchange.getResponseBody().write(body);
            }
          });
      server.start();
      return server;
    } catch (IOException e) {
      throw new IllegalStateException("Cannot start the local smoke backend", e);
    }
  }
}
