package com.sanigest.catalog.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
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
import java.util.UUID;
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
        .containsExactly("public.flyway_schema_history");
    assertThat(flyway.info().applied())
        .singleElement()
        .satisfies(migration ->
            assertThat(migration.getScript()).isEqualTo("V1__create_catalog_schema.sql"));
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
    var claims =
        new JWTClaimsSet.Builder()
            .issuer(issuer)
            .audience(audience)
            .subject(UUID.randomUUID().toString())
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
