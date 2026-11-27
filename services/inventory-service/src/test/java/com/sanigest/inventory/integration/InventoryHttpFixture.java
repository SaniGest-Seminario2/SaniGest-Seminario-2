package com.sanigest.inventory.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sanigest.inventory.application.usecase.ReconcileStock;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class InventoryHttpFixture {
  static final String STOCK = "/api/v1/inventory/stock";
  static final String WAREHOUSES = "/api/v1/warehouses";
  static final String MOVEMENTS = "/api/v1/inventory/movements";
  static final String ADJUST = "/api/v1/inventory/adjustments";
  static final String SUMMARY = "/api/v1/inventory/summary";
  static final KeyPair USER_KEYS = keys();
  static final KeyPair SERVICE_KEYS = keys();
  static final WireMockServer UPSTREAM = new WireMockServer(0);

  @Container
  static final PostgreSQLContainer DATABASE =
      new PostgreSQLContainer("postgres:17.7-alpine")
          .withDatabaseName("sanigest_inventory")
          .withUsername("sanigest_inventory")
          .withPassword(UUID.randomUUID().toString());

  private static HttpServer companyServer;
  private static final Map<UUID, Member> MEMBERS = new ConcurrentHashMap<>();

  private record Member(UUID actor, String token, String role, boolean grant) {}

  @TempDir static Path credentials;
  @LocalServerPort private int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  @Autowired ReconcileStock reconcile;

  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
  final UUID actor = UUID.randomUUID();
  final UUID company = UUID.randomUUID();
  final UUID other = UUID.randomUUID();
  final UUID item = UUID.randomUUID();
  String token;

  @DynamicPropertySource
  static void settings(DynamicPropertyRegistry registry) throws Exception {
    if (!UPSTREAM.isRunning()) {
      UPSTREAM.start();
    }
    var userKey = pem("user", "PUBLIC KEY", USER_KEYS.getPublic().getEncoded());
    var serviceKey = pem("inventory", "PRIVATE KEY", SERVICE_KEYS.getPrivate().getEncoded());
    registry.add("sanigest.jwt.public-key", () -> userKey);
    registry.add("sanigest.company-access.private-key-location", () -> serviceKey);
    companyServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    companyServer.createContext(
        "/internal/v2/company-access", InventoryHttpFixture::respondToCompany);
    companyServer.start();
    registry.add(
        "sanigest.company-access.origin",
        () -> "http://127.0.0.1:" + companyServer.getAddress().getPort());
    registry.add("sanigest.catalog.origin", UPSTREAM::baseUrl);
    registry.add("INVENTORY_DB_HOST", DATABASE::getHost);
    registry.add("INVENTORY_DB_PORT", () -> DATABASE.getMappedPort(5432));
    registry.add("INVENTORY_DB_PASSWORD", DATABASE::getPassword);
  }

  @AfterAll
  static void stopUpstream() {
    UPSTREAM.stop();
    companyServer.stop(0);
  }

  @BeforeEach
  void prepare() throws Exception {
    UPSTREAM.resetAll();
    MEMBERS.clear();
    token = token(actor, USER_KEYS, "sanigest-api-test");
    membership(company, "OWNER", false);
    membership(other, "VIEWER", false);
    catalog(company, item, "PRODUCT", true, "UNIT", false);
  }

  void membership(UUID tenant, String role, boolean grant) {
    MEMBERS.put(tenant, new Member(actor, token, role, grant));
  }

  void revoke(UUID tenant) {
    MEMBERS.remove(tenant);
  }

  private static void respondToCompany(HttpExchange exchange) throws java.io.IOException {
    try (exchange) {
      String encoded = exchange.getRequestHeaders().getFirst("Authorization");
      var jwt = SignedJWT.parse(encoded.substring("Bearer ".length()));
      var claims = jwt.getJWTClaimsSet();
      var tenant = UUID.fromString(claims.getStringClaim("company_id"));
      var scope = claims.getStringClaim("scope");
      if (!validAssertion(jwt, claims, scope)) {
        exchange.sendResponseHeaders(401, -1);
        return;
      }
      var member = MEMBERS.get(tenant);
      if (member == null
          || !member.actor().toString().equals(claims.getSubject())
          || !member
              .token()
              .equals(exchange.getRequestHeaders().getFirst("X-Company-User-Assertion"))) {
        exchange.sendResponseHeaders(403, -1);
        return;
      }
      byte[] response =
          """
          {"companyId":"%s","userId":"%s","module":"INVENTORY","scope":"%s",
           "mode":"MEMBERSHIP","role":"%s","explicitGrant":%s}
          """
              .formatted(tenant, member.actor(), scope, member.role(), member.grant())
              .getBytes(java.nio.charset.StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, response.length);
      exchange.getResponseBody().write(response);
    } catch (com.nimbusds.jose.JOSEException | java.text.ParseException malformed) {
      throw new java.io.IOException(malformed);
    }
  }

  private static boolean validAssertion(SignedJWT jwt, JWTClaimsSet claims, String scope)
      throws com.nimbusds.jose.JOSEException, java.text.ParseException {
    return jwt.verify(new RSASSAVerifier((RSAPublicKey) SERVICE_KEYS.getPublic()))
        && JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())
        && "sanigest-service+jwt".equals(jwt.getHeader().getType().toString())
        && "inventory-company-access".equals(claims.getIssuer())
        && java.util.List.of("sanigest-company-access-v2").equals(claims.getAudience())
        && "INVENTORY".equals(claims.getStringClaim("module"))
        && "MEMBERSHIP".equals(claims.getStringClaim("mode"))
        && claims.getExpirationTime().toInstant().isAfter(Instant.now())
        && ("READ".equals(scope) || "WRITE".equals(scope));
  }

  void catalog(
      UUID tenant, UUID article, String type, boolean active, String unit, boolean divisible) {
    UPSTREAM.stubFor(
        get(urlPathEqualTo("/api/v1/catalog-items/" + article))
            .withHeader("Authorization", equalTo("Bearer " + token))
            .withHeader("X-Company-Id", equalTo(tenant.toString()))
            .withHeader("X-Trace-Id", matching("[0-9a-f]{32}"))
            .withQueryParam("expectedCompanyId", equalTo(tenant.toString()))
            .willReturn(
                okJson(
                        """
                        {"id":"%s","type":"%s","active":%s,"unitCode":"%s","unitDivisible":%s,
                         "sku":"SKU-001","name":"Producto Inventario"}
                        """
                            .formatted(article, type, active, unit, divisible))
                    .withHeader("X-Catalog-Company-Id", tenant.toString())));
  }

  void catalogFailure(UUID article, int status) {
    UPSTREAM.stubFor(
        get(urlPathEqualTo("/api/v1/catalog-items/" + article))
            .willReturn(aResponse().withStatus(status)));
  }

  UUID warehouse() throws Exception {
    return UUID.fromString(
        expect(
                "POST",
                WAREHOUSES,
                company,
                null,
                "{\"code\":\"ALM-1\",\"name\":\"Warehouse\"}",
                201)
            .get("id")
            .asString());
  }

  String position(UUID warehouse) {
    return STOCK + "/" + item + "/" + warehouse;
  }

  JsonNode initialize(UUID warehouse, String amount) throws Exception {
    return expect("PUT", position(warehouse), company, "initial", config(true, amount), 200);
  }

  static String config(boolean tracking, String amount) {
    return "{\"trackingEnabled\":"
        + tracking
        + (amount == null ? "" : ",\"initialQuantity\":\"" + amount + "\"")
        + "}";
  }

  String adjustment(UUID warehouse, String direction, String quantity) {
    return adjustment(item, warehouse, direction, quantity);
  }

  static String adjustment(UUID article, UUID warehouse, String direction, String quantity) {
    return """
    {"catalogItemId":"%s","warehouseId":"%s","direction":"%s","quantity":"%s","reason":"Corrección"}
    """
        .formatted(article, warehouse, direction, quantity);
  }

  JsonNode expect(String method, String path, UUID tenant, String key, String body, int status)
      throws Exception {
    var response = send(method, path, tenant, key, body, token);
    assertThat(response.statusCode())
        .as("%s %s: %s", method, path, response.body())
        .isEqualTo(status);
    return response.body().isEmpty() ? json.nullNode() : json.readTree(response.body());
  }

  HttpResponse<String> send(
      String method, String path, UUID tenant, String key, String body, String bearer)
      throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .timeout(Duration.ofSeconds(8));
    if (bearer != null) {
      request.header("Authorization", "Bearer " + bearer);
    }
    if (tenant != null) {
      request.header("X-Company-Id", tenant.toString());
    }
    if (key != null) {
      request.header("Idempotency-Key", key);
    }
    if (body != null) {
      request.header("Content-Type", "application/json");
    }
    return http.send(
        request
            .method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  static String token(UUID user, KeyPair keys, String audience) throws Exception {
    var now = Instant.now();
    var claims =
        new JWTClaimsSet.Builder()
            .issuer("sanigest-test")
            .audience(audience)
            .subject(user.toString())
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(120)))
            .claim("accountType", "REGULAR")
            .build();
    var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
    jwt.sign(new RSASSASigner(keys.getPrivate()));
    return jwt.serialize();
  }

  private static KeyPair keys() {
    try {
      var generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (GeneralSecurityException failure) {
      throw new IllegalStateException(failure);
    }
  }

  private static String pem(String name, String label, byte[] encoded) throws Exception {
    return Files.writeString(
            credentials.resolve(name + ".pem"),
            "-----BEGIN "
                + label
                + "-----\n"
                + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded)
                + "\n-----END "
                + label
                + "-----\n")
        .toUri()
        .toString();
  }

  BigDecimal physical(UUID warehouse) {
    return jdbc.queryForObject(
        "SELECT on_hand FROM inventory.stock_positions WHERE company_id=? AND warehouse_id=?",
        BigDecimal.class,
        company,
        warehouse);
  }

  long countMovements(UUID warehouse) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM inventory.stock_movements WHERE company_id=? AND warehouse_id=?",
        Long.class,
        company,
        warehouse);
  }
}
