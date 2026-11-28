package com.sanigest.identity.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sanigest.identity.application.dto.registration.RegisterUserCommand;
import com.sanigest.identity.application.exception.registration.DuplicateEmailException;
import com.sanigest.identity.application.port.out.security.password.PasswordHasher;
import com.sanigest.identity.application.usecase.registration.RegisterUserUseCase;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Tag("regression")
@Testcontainers
@ActiveProfiles("local")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IdentityServiceApplicationIT {

  private static final String DB_PASSWORD = TestCredentials.random();
  private static final String VALID_PASSWORD = TestCredentials.random();
  private static final String ANOTHER_PASSWORD = TestCredentials.random();
  private static final String RAW_PASSWORD = TestCredentials.random();

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17.7-alpine")
          .withDatabaseName("sanigest_identity")
          .withUsername("sanigest_identity")
          .withPassword(DB_PASSWORD)
          .withEnv("POSTGRES_PASSWORD", DB_PASSWORD);

  @LocalServerPort private int port;

  @Autowired private Flyway flyway;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private RegisterUserUseCase registerUserService;
  @Autowired private PasswordHasher passwordHasher;

  private final HttpClient httpClient = HttpClient.newHttpClient();

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("sanigest.jwt.issuer", () -> "sanigest-it");
    registry.add("sanigest.jwt.audience", () -> "sanigest-api-it");
  }

  @BeforeEach
  void cleanUsers() {
    jdbcTemplate.update("DELETE FROM refresh_tokens");
    jdbcTemplate.update("DELETE FROM users");
  }

  @Test
  void appliesMigrationAndKeepsItIdempotent() {
    Integer applied =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM "flyway_schema_history"
            WHERE "version" = '1' AND "success" = TRUE
            """,
            Integer.class);

    assertThat(applied).isEqualTo(1);
    assertThat(flyway.migrate().migrationsExecuted).isZero();
  }

  @Test
  void registersARegularUserThroughThePublicApi() throws Exception {
    HttpResponse<String> response =
        post(registerPayload(" Person@Example.COM ", VALID_PASSWORD, "Person"));

    assertThat(response.statusCode()).isEqualTo(201);
    assertThat(response.body())
        .contains("\"userId\":", "\"email\":\"person@example.com\"")
        .doesNotContain("password", "passwordHash");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = 'person@example.com'", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void rejectsInvalidEmailAndWeakPasswordThroughThePublicApi() throws Exception {
    HttpResponse<String> response = post(registerPayload("not-an-email", "short", "Person"));

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.body())
        .contains("urn:sanigest:problem:validation", "\"email\"", "\"password\"");
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isZero();
  }

  @Test
  void returnsConflictForADuplicateEmailRegardlessOfCasing() throws Exception {
    assertThat(post(registerPayload("duplicate@example.com", VALID_PASSWORD, "First")).statusCode())
        .isEqualTo(201);

    HttpResponse<String> duplicate =
        post(registerPayload("DUPLICATE@EXAMPLE.COM", ANOTHER_PASSWORD, "Second"));

    assertThat(duplicate.statusCode()).isEqualTo(409);
    assertThat(duplicate.body()).contains("urn:sanigest:problem:duplicate-email");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = 'duplicate@example.com'", Integer.class))
        .isEqualTo(1);
  }

  @Test
  @Transactional
  void persistsAUserWithoutPersistingThePlainTextPassword() {
    var user =
        registerUserService.register(
            new RegisterUserCommand(" Test.User@Example.COM ", RAW_PASSWORD, "Test User"));

    String storedEmail =
        jdbcTemplate.queryForObject(
            "SELECT email FROM users WHERE id = ?", String.class, user.id());
    String storedHash =
        jdbcTemplate.queryForObject(
            "SELECT password_hash FROM users WHERE id = ?", String.class, user.id());

    assertThat(storedEmail).isEqualTo("test.user@example.com");
    assertThat(storedHash).isNotEqualTo(RAW_PASSWORD).startsWith("$2");
    assertThat(passwordHasher.matches(RAW_PASSWORD, storedHash)).isTrue();
  }

  @Test
  @Transactional
  void treatsDifferentlyCasedEmailsAsTheSameAccount() {
    registerUserService.register(
        new RegisterUserCommand("unique@example.com", VALID_PASSWORD, "First"));

    assertThatThrownBy(() ->
            registerUserService.register(
                new RegisterUserCommand("UNIQUE@EXAMPLE.COM", ANOTHER_PASSWORD, "Second")))
        .isInstanceOf(DuplicateEmailException.class);
  }

  @Test
  @Transactional
  void databaseRejectsNonNormalizedEmailEvenWhenBypassingTheApplication() {
    assertThatThrownBy(() ->
            jdbcTemplate.update(
                """
                INSERT INTO users
                  (id, email, password_hash, name, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                UUID.randomUUID(),
                "Upper@Example.com",
                "$2a$12$not-a-real-password-hash",
                "Bypass",
                "ACTIVE"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void exposesOnlySafeOperationalEndpoints() throws Exception {
    HttpResponse<String> health = get("/actuator/health");
    HttpResponse<String> liveness = get("/actuator/health/liveness");
    HttpResponse<String> readiness = get("/actuator/health/readiness");
    HttpResponse<String> info = get("/actuator/info");
    HttpResponse<String> env = get("/actuator/env");

    assertThat(health.statusCode()).isEqualTo(200);
    assertThat(health.body()).contains("\"status\":\"UP\"").doesNotContain("components");
    assertThat(liveness.statusCode()).isEqualTo(200);
    assertThat(readiness.statusCode()).isEqualTo(200);
    assertThat(info.statusCode()).isEqualTo(200);
    assertThat(info.body())
        .contains("\"artifact\":\"identity-service\"")
        .doesNotContain("password");
    assertThat(env.statusCode()).isEqualTo(401);
  }

  @Test
  void logsInPersistedActiveUsersAndReturnsOnlyTheTokenContract() throws Exception {
    assertThat(post(registerPayload("login@example.com", VALID_PASSWORD, "Login")).statusCode())
        .isEqualTo(201);

    HttpResponse<String> response = login("login@example.com", VALID_PASSWORD);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body())
        .contains("\"accessToken\":", "\"tokenType\":\"Bearer\"", "\"expiresIn\":")
        .doesNotContain("password", "passwordHash");
  }

  @Test
  void rejectsInvalidCredentialsInactiveUsersAndMissingBearerTokensGenerically() throws Exception {
    assertThat(
            post(registerPayload("inactive@example.com", VALID_PASSWORD, "Inactive")).statusCode())
        .isEqualTo(201);
    jdbcTemplate.update(
        "UPDATE users SET status = 'DISABLED' WHERE email = 'inactive@example.com'");

    HttpResponse<String> wrong = login("missing@example.com", "wrong-password");
    HttpResponse<String> inactive = login("inactive@example.com", VALID_PASSWORD);
    HttpResponse<String> unauthenticated = get("/api/v1/private-resource");

    assertThat(wrong.statusCode()).isEqualTo(401);
    assertThat(inactive.statusCode()).isEqualTo(401);
    assertThat(wrong.body()).contains("Invalid credentials").doesNotContain("missing@example.com");
    assertThat(inactive.body()).contains("Invalid credentials").doesNotContain("inactive");
    assertThat(unauthenticated.statusCode()).isEqualTo(401);
  }

  private HttpResponse<String> get(String path) throws Exception {
    return httpClient.send(
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> post(String json) throws Exception {
    return httpClient.send(
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/auth/register"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> login(String email, String password) throws Exception {
    String json = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password);
    return httpClient.send(
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/auth/login"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private static String registerPayload(String email, String pw, String name) {
    return String.format(
        "{\"email\":\"%s\",\"%s\":\"%s\",\"name\":\"%s\"}", email, "password", pw, name);
  }
}
