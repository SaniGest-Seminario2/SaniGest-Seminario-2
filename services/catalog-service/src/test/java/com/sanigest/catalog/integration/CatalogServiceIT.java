package com.sanigest.catalog.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sanigest.catalog.application.ClientCreationAuthorization;
import com.sanigest.catalog.application.ClientCreationException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"local", "test"})
@Testcontainers
class CatalogServiceIT {
  private static final String CATALOG = "sanigest_catalog";
  private static final String ISSUER = "sanigest-test";
  private static final String AUDIENCE = "sanigest-api-test";
  private static final Map<String, String> PASSWORDS =
      Map.of(
          "IDENTITY_DB_PASSWORD", UUID.randomUUID().toString(),
          "COMPANY_DB_PASSWORD", UUID.randomUUID().toString(),
          "CATALOG_DB_PASSWORD", UUID.randomUUID().toString(),
          "SALES_DB_PASSWORD", UUID.randomUUID().toString(),
          "EXPENSE_DB_PASSWORD", UUID.randomUUID().toString(),
          "FINANCE_DB_PASSWORD", UUID.randomUUID().toString());
  private static final KeyPair KEY = rsaKey();
  private static final Path PUBLIC_KEY = writePublicKey();

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17.7-alpine")
          .withDatabaseName("postgres")
          .withUsername("sanigest_admin")
          .withPassword(UUID.randomUUID().toString())
          .withEnv(PASSWORDS)
          .withCopyFileToContainer(
              MountableFile.forHostPath(
                  "../../infrastructure/postgres/init/10-create-service-databases.sh", 0755),
              "/docker-entrypoint-initdb.d/10-create-service-databases.sh");

  @MockitoBean ClientCreationAuthorization authorization;
  @LocalServerPort int port;
  @Autowired DataSource dataSource;
  @Autowired EntityManagerFactory entityManagerFactory;
  @Autowired Flyway flyway;
  @Autowired Environment environment;

  @DynamicPropertySource
  static void configure(DynamicPropertyRegistry registry) {
    registry.add("CATALOG_DB_HOST", POSTGRES::getHost);
    registry.add("CATALOG_DB_PORT", () -> POSTGRES.getMappedPort(5432));
    registry.add("CATALOG_DB_PASSWORD", () -> PASSWORDS.get("CATALOG_DB_PASSWORD"));
    registry.add("JWT_PUBLIC_KEY_LOCATION", () -> PUBLIC_KEY.toUri().toString());
  }

  @AfterAll
  static void removePublicKey() throws IOException {
    Files.deleteIfExists(PUBLIC_KEY);
  }

  @Test
  @Tag("smoke")
  void servesPublicHealthProbesAndOpenApi() throws Exception {
    try (var http = HttpClient.newHttpClient()) {
      var mapper = JsonMapper.shared();
      for (String path :
          new String[] {
            "/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"
          }) {
        var response = get(http, path, null);
        assertThat(response.statusCode()).isEqualTo(200);
        var health = mapper.readTree(response.body());
        assertThat(health.path("status").asString()).isEqualTo("UP");
        if (path.equals("/actuator/health")) {
          assertThat(health)
              .isEqualTo(
                  mapper.readTree("{\"status\":\"UP\",\"groups\":[\"liveness\",\"readiness\"]}"));
        } else {
          assertThat(health).isEqualTo(mapper.readTree("{\"status\":\"UP\"}"));
        }
      }
      var response = get(http, "/v3/api-docs", null);
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.headers().firstValue("Content-Type"))
          .hasValueSatisfying(value -> assertThat(value).startsWith("application/json"));
      var document = mapper.readTree(response.body());
      assertThat(document.path("openapi").asString()).isEqualTo("3.0.3");
      assertThat(document.path("info").path("title").asString())
          .isEqualTo("SaniGest Catalog Service");
      assertThat(
              document
                  .path("paths")
                  .path("/actuator/health")
                  .path("get")
                  .path("responses")
                  .has("200"))
          .isTrue();
    }
  }

  @Test
  @Tag("regression")
  void validatesJwtAndDeniesBusinessAccessWithoutCompanyAuthorization() throws Exception {
    var now = Instant.now();
    try (var http = HttpClient.newHttpClient()) {
      String path = "/api/v1/products?companyId=" + UUID.randomUUID();
      assertThat(get(http, path, null).statusCode()).isEqualTo(401);
      assertThat(get(http, path, "invalid").statusCode()).isEqualTo(401);
      assertThat(get(http, path, token(KEY, ISSUER, AUDIENCE, now.plusSeconds(120))).statusCode())
          .isEqualTo(403);
      assertThat(
              get(http, path, token(rsaKey(), ISSUER, AUDIENCE, now.plusSeconds(120))).statusCode())
          .isEqualTo(401);
      assertThat(
              get(http, path, token(KEY, "other-issuer", AUDIENCE, now.plusSeconds(120)))
                  .statusCode())
          .isEqualTo(401);
      assertThat(
              get(http, path, token(KEY, ISSUER, "other-audience", now.plusSeconds(120)))
                  .statusCode())
          .isEqualTo(401);
      assertThat(get(http, path, token(KEY, ISSUER, AUDIENCE, now.minusSeconds(120))).statusCode())
          .isEqualTo(401);
    }
  }

  @Test
  @Tag("regression")
  void startsJpaInValidationModeAndAppliesOnlyCatalogMigrations() {
    var jdbc = new JdbcTemplate(dataSource);
    assertThat(entityManagerFactory.isOpen()).isTrue();
    assertThat(entityManagerFactory.getProperties())
        .containsEntry("hibernate.hbm2ddl.auto", "validate");
    assertThat(environment.matchesProfiles("local & test")).isTrue();
    assertThat(jdbc.queryForMap("SELECT current_database() AS db, session_user AS role"))
        .containsAllEntriesOf(Map.of("db", CATALOG, "role", CATALOG));
    assertThat(
            jdbc.queryForObject(
                "SELECT schema_owner FROM information_schema.schemata WHERE schema_name = 'catalog'",
                String.class))
        .isEqualTo(CATALOG);
    assertThat(
            jdbc.queryForList(
                "SELECT schemaname || '.' || tablename FROM pg_tables WHERE schemaname IN ('public', 'catalog')",
                String.class))
        .containsExactlyInAnyOrder("public.flyway_schema_history", "catalog.clients");
    assertThat(flyway.info().applied())
        .extracting(migration -> migration.getScript())
        .containsExactly("V1__create_catalog_schema.sql", "V2__create_clients.sql");
    flyway.validate();
    assertThat(flyway.migrate().migrationsExecuted).isZero();
  }

  @Test
  @Tag("regression")
  void postgresRejectsCrossServiceCredentialsAndConnections() {
    for (String neighbor : new String[] {"identity", "company"}) {
      String database = "sanigest_" + neighbor;
      String neighborPassword = PASSWORDS.get(neighbor.toUpperCase(Locale.ROOT) + "_DB_PASSWORD");
      assertConnectionRejected(database, CATALOG, PASSWORDS.get("CATALOG_DB_PASSWORD"), "42501");
      assertConnectionRejected(CATALOG, database, neighborPassword, "42501");
      assertConnectionRejected(CATALOG, CATALOG, neighborPassword, "28P01");
    }
  }

  @Test
  @Tag("regression")
  void connectionPoolRejectsPrivilegedCredentialsBeforeUse() {
    var config = new HikariConfig();
    config.setJdbcUrl(POSTGRES.getJdbcUrl().replaceFirst("/postgres$", "/" + CATALOG));
    config.setUsername(POSTGRES.getUsername());
    config.setPassword(POSTGRES.getPassword());
    config.setConnectionInitSql(
        environment.getRequiredProperty("spring.datasource.hikari.connection-init-sql"));

    assertThatThrownBy(
            () -> {
              try (var unexpected = new HikariDataSource(config)) {
                assertThat(unexpected.isClosed()).isFalse();
              }
            })
        .rootCause()
        .hasMessageContaining("Catalog requires the sanigest_catalog database and role");
  }

  @Test
  @Tag("smoke")
  void createsAndPersistsOnlyInTheAuthorizedCompanyThenRejectsADuplicate() throws Exception {
    var companyId = UUID.randomUUID();
    var otherCompanyId = UUID.randomUUID();
    var taxId = UUID.randomUUID().toString();
    var token = authorizedToken(companyId);
    try (var http = HttpClient.newHttpClient()) {
      var response =
          http.send(
              clientRequest(
                  token,
                  """
                  {"name":" Client ","taxId":" %s ","email":"sample@example.org","phone":"809-555-1234",
                   "address":"Santo Domingo","companyId":"%s","userId":"%s","status":"DISABLED","id":"%s"}
                  """
                      .formatted(taxId, otherCompanyId, UUID.randomUUID(), UUID.randomUUID()),
                  otherCompanyId),
              HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).isEqualTo(201);
      var mapper = JsonMapper.shared();
      var body = mapper.readTree(response.body());
      var id = UUID.fromString(body.path("id").asString());
      assertThat(body)
          .isEqualTo(
              mapper.readTree(
                  """
                  {"id":"%s","name":"Client","taxId":"%s","status":"ACTIVE"}
                  """
                      .formatted(id, taxId)));
      var row =
          new JdbcTemplate(dataSource)
              .queryForMap(
                  "SELECT company_id, name, tax_id, status, email, phone, address FROM catalog.clients WHERE id = ?",
                  id);
      assertThat(row)
          .containsExactlyInAnyOrderEntriesOf(
              Map.of(
                  "company_id",
                  companyId,
                  "name",
                  "Client",
                  "tax_id",
                  taxId,
                  "status",
                  "ACTIVE",
                  "email",
                  "sample@example.org",
                  "phone",
                  "809-555-1234",
                  "address",
                  "Santo Domingo"));

      var duplicate =
          http.send(
              clientRequest(token, withTaxId(taxId), otherCompanyId),
              HttpResponse.BodyHandlers.ofString());
      assertThat(duplicate.statusCode()).isEqualTo(409);
      assertThat(mapper.readTree(duplicate.body()).path("errorCode").asString())
          .isEqualTo("DUPLICATE_TAX_ID");
    }
  }

  @Test
  @Tag("regression")
  void enforcesUniquenessWithinOneCompanyEvenAcrossOverlappingPosts() throws Exception {
    var companyId = UUID.randomUUID();
    var userId = UUID.randomUUID();
    var token = token(KEY, ISSUER, AUDIENCE, Instant.now().plusSeconds(120), userId, "REGULAR");
    var overlappingRequests = new CyclicBarrier(2);
    when(authorization.authorizedCompany(userId, token))
        .thenAnswer(invocation -> {
          overlappingRequests.await(5, TimeUnit.SECONDS);
          return Optional.of(companyId);
        });
    var taxId = UUID.randomUUID().toString();
    var request = clientRequest(token, withTaxId(taxId), null);
    try (var http = HttpClient.newHttpClient()) {
      var first = http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
      var second = http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
      assertThat(
              new int[] {
                first.get(10, TimeUnit.SECONDS).statusCode(),
                second.get(10, TimeUnit.SECONDS).statusCode()
              })
          .containsExactlyInAnyOrder(201, 409);
      assertThat(postStatus(http, authorizedToken(UUID.randomUUID()), withTaxId(taxId)))
          .isEqualTo(201);
      var nullTaxIdToken = authorizedToken(companyId);
      assertThat(postStatus(http, nullTaxIdToken, "{\"name\":\"First\"}")).isEqualTo(201);
      assertThat(postStatus(http, nullTaxIdToken, "{\"name\":\"Second\",\"taxId\":null}"))
          .isEqualTo(201);
      var jdbc = new JdbcTemplate(dataSource);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM catalog.clients WHERE company_id = ? AND tax_id = ?",
                  Long.class,
                  companyId,
                  taxId))
          .isEqualTo(1L);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM catalog.clients WHERE company_id = ? AND tax_id IS NULL",
                  Long.class,
                  companyId))
          .isEqualTo(2L);
    }
  }

  @Test
  @Tag("regression")
  void deniesPostsWithInvalidAuthenticationOrWithoutCurrentCompanyPermission() throws Exception {
    var userId = UUID.randomUUID();
    var now = Instant.now();
    var denied = token(KEY, ISSUER, AUDIENCE, now.plusSeconds(120), userId, "ADVISOR");
    when(authorization.authorizedCompany(userId, denied)).thenReturn(Optional.empty());
    var regularUser = UUID.randomUUID();
    var withoutPermission =
        token(KEY, ISSUER, AUDIENCE, now.plusSeconds(120), regularUser, "REGULAR");
    when(authorization.authorizedCompany(regularUser, withoutPermission))
        .thenReturn(Optional.empty());
    var unavailable = token(KEY, ISSUER, AUDIENCE, now.plusSeconds(120), userId, "REGULAR");
    when(authorization.authorizedCompany(userId, unavailable))
        .thenThrow(
            new ClientCreationException(ClientCreationException.Reason.AUTHORIZATION_UNAVAILABLE));
    var companyId = UUID.randomUUID();
    var taxId = UUID.randomUUID().toString();
    var body =
        """
        {"name":"Denied","taxId":"%s","companyId":"%s","userId":"%s"}
        """
            .formatted(taxId, companyId, UUID.randomUUID());
    try (var http = HttpClient.newHttpClient()) {
      assertThat(postStatus(http, null, body)).isEqualTo(401);
      assertThat(postStatus(http, "invalid", body)).isEqualTo(401);
      assertThat(postStatus(http, token(KEY, ISSUER, AUDIENCE, now.minusSeconds(120)), body))
          .isEqualTo(401);
      assertThat(postStatus(http, token(rsaKey(), ISSUER, AUDIENCE, now.plusSeconds(120)), body))
          .isEqualTo(401);
      assertThat(
              http.send(
                      clientRequest(denied, body, companyId), HttpResponse.BodyHandlers.ofString())
                  .statusCode())
          .isEqualTo(403);
      assertThat(postStatus(http, withoutPermission, body)).isEqualTo(403);
      assertThat(postStatus(http, unavailable, body)).isEqualTo(503);
    }
    assertThat(
            new JdbcTemplate(dataSource)
                .queryForObject(
                    "SELECT count(*) FROM catalog.clients WHERE tax_id = ?", Long.class, taxId))
        .isZero();
  }

  private String authorizedToken(UUID companyId) throws JOSEException {
    var userId = UUID.randomUUID();
    var jwt = token(KEY, ISSUER, AUDIENCE, Instant.now().plusSeconds(120), userId, "REGULAR");
    when(authorization.authorizedCompany(userId, jwt)).thenReturn(Optional.of(companyId));
    return jwt;
  }

  private static String withTaxId(String taxId) {
    return "{\"name\":\"Client\",\"taxId\":\"%s\"}".formatted(taxId);
  }

  private int postStatus(HttpClient http, String token, String body)
      throws IOException, InterruptedException {
    return http.send(clientRequest(token, body, null), HttpResponse.BodyHandlers.ofString())
        .statusCode();
  }

  private HttpRequest clientRequest(String token, String body, UUID untrustedCompanyId) {
    var request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/clients"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));
    if (token != null) {
      request.header("Authorization", "Bearer " + token);
    }
    if (untrustedCompanyId != null) {
      request.header("X-Company-Id", untrustedCompanyId.toString());
    }
    return request.build();
  }

  private static void assertConnectionRejected(
      String database, String user, String password, String state) {
    String url =
        "jdbc:postgresql://%s:%d/%s"
            .formatted(POSTGRES.getHost(), POSTGRES.getMappedPort(5432), database);
    assertThatThrownBy(
            () -> {
              try (var unexpected = DriverManager.getConnection(url, user, password)) {
                assertThat(unexpected.isClosed()).isFalse();
              }
            })
        .isInstanceOfSatisfying(
            SQLException.class, failure -> assertThat(failure.getSQLState()).isEqualTo(state));
  }

  private HttpResponse<String> get(HttpClient http, String path, String token)
      throws IOException, InterruptedException {
    var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
    if (token != null) {
      request.header("Authorization", "Bearer " + token);
    }
    return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private static String token(KeyPair key, String issuer, String audience, Instant expiration)
      throws JOSEException {
    return token(key, issuer, audience, expiration, UUID.randomUUID(), "REGULAR");
  }

  private static String token(
      KeyPair key,
      String issuer,
      String audience,
      Instant expiration,
      UUID userId,
      String accountType)
      throws JOSEException {
    var claims =
        new JWTClaimsSet.Builder()
            .issuer(issuer)
            .audience(audience)
            .subject(userId.toString())
            .claim("accountType", accountType)
            .issueTime(Date.from(Instant.now().minusSeconds(300)))
            .expirationTime(Date.from(expiration))
            .build();
    var jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).build(), claims);
    jwt.sign(new RSASSASigner(key.getPrivate()));
    return jwt.serialize();
  }

  private static KeyPair rsaKey() {
    try {
      var generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("Cannot generate a test RSA key", exception);
    }
  }

  private static Path writePublicKey() {
    try {
      var file = Files.createTempFile("catalog-jwt-", ".pem");
      var encoded = Base64.getMimeEncoder().encodeToString(KEY.getPublic().getEncoded());
      return Files.writeString(
          file, "-----BEGIN PUBLIC KEY-----\n" + encoded + "\n-----END PUBLIC KEY-----\n");
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }
}
