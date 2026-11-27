package com.sanigest.finance.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.server.ResponseStatusException;

class FinancialSourcesTest {
  private final UUID company = UUID.randomUUID();
  private HttpServer server;
  private volatile int status = 200;
  private volatile String payload = "{\"totalElements\":7}";

  @BeforeEach
  void startSource() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          try (exchange) {
            var headers = exchange.getRequestHeaders();
            String bodyToSend = payload;
            if (!company.toString().equals(headers.getFirst("X-Company-Id"))
                || !"Bearer token".equals(headers.getFirst("Authorization"))) {
              exchange.sendResponseHeaders(403, -1);
            } else if ("HEAD".equals(exchange.getRequestMethod()) || bodyToSend == null) {
              exchange.sendResponseHeaders(status, -1);
            } else {
              byte[] body = bodyToSend.getBytes(StandardCharsets.UTF_8);
              exchange.getResponseHeaders().set("Content-Type", "application/json");
              exchange.sendResponseHeaders(status, body.length);
              exchange.getResponseBody().write(body);
            }
          }
        });
    server.start();
  }

  @AfterEach
  void stopSource() {
    server.stop(0);
  }

  @Test
  void authorizesReadsAndStoresSourceSnapshotsOnRebuild() {
    var jdbc = mock(JdbcClient.class);
    var sql = mock(JdbcClient.StatementSpec.class, RETURNS_SELF);
    var query = stringQuery();
    when(jdbc.sql(anyString())).thenReturn(sql);
    when(sql.query(String.class)).thenReturn(query);
    when(query.single()).thenReturn("{\"partial\":false}");
    var projections = projections(jdbc);

    assertEquals("{\"partial\":false}", projections.read(company, "token"));
    assertEquals("{\"partial\":false}", projections.rebuild(company, "token"));
    verify(sql).param("sales", payload);
    verify(sql).param("expense", payload);
    verify(sql).update();
  }

  @Test
  void rejectsUnavailableOrForbiddenSourcesBeforeAccessingStorage() {
    var jdbc = mock(JdbcClient.class);
    var projections = projections(jdbc);
    status = 403;
    assertStatus(403, () -> projections.read(company, "token"));
    status = 401;
    assertStatus(401, () -> projections.rebuild(company, "token"));
    status = 502;
    assertStatus(503, () -> projections.rebuild(company, "token"));
    status = 200;
    payload = null;
    assertStatus(503, () -> projections.rebuild(company, "token"));
    verifyNoInteractions(jdbc);
  }

  @Test
  void pendingInvoiceClientMapsMissingCountsAndUpstreamErrors() {
    var invoices = new SalesPendingInvoices(origin());
    assertEquals(7, invoices.count(company, "token"));
    payload = "{}";
    assertStatus(503, () -> invoices.count(company, "token"));
    status = 403;
    assertStatus(403, () -> invoices.count(company, "token"));
    status = 401;
    assertStatus(401, () -> invoices.count(company, "token"));
    status = 502;
    assertStatus(503, () -> invoices.count(company, "token"));
  }

  private FinancialProjections projections(JdbcClient jdbc) {
    return new FinancialProjections(
        jdbc, new FinancialSources(origin(), origin(), Duration.ofSeconds(2), null));
  }

  private String origin() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  private static void assertStatus(int status, Runnable request) {
    assertEquals(
        status, assertThrows(ResponseStatusException.class, request::run).getStatusCode().value());
  }

  @SuppressWarnings("unchecked")
  private static JdbcClient.MappedQuerySpec<String> stringQuery() {
    return mock(JdbcClient.MappedQuerySpec.class);
  }
}
