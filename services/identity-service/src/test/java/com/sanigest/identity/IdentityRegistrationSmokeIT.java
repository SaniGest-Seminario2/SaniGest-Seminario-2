package com.sanigest.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.sanigest.identity.domain.port.PasswordHasher;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Tag("smoke")
@Testcontainers
@ActiveProfiles("local")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(IdentityRegistrationSmokeIT.SmokeScenarioReporter.class)
class IdentityRegistrationSmokeIT {

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17.7-alpine")
          .withDatabaseName("sanigest_identity")
          .withUsername("sanigest_identity")
          .withPassword("identity-smoke-password");

  private final HttpClient httpClient = HttpClient.newHttpClient();

  @LocalServerPort private int port;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PasswordHasher passwordHasher;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @BeforeEach
  void cleanUsers() {
    jdbcTemplate.update("DELETE FROM users");
  }

  private static final String VALID_PASSWORD = "correct-horse-battery-staple";
  private static final String ANOTHER_PASSWORD = "second-secure-password";

  @Test
  @DisplayName("POST /api/v1/auth/register -> 201 and persists a BCrypt hash in PostgreSQL")
  void registersThroughRealHttpAndPersistsOnlyBcryptInPostgresql() throws Exception {
    Response response =
        register(
            "{\"email\":\" Smoke.User@Example.COM \","
                + "\"password\":\""
                + VALID_PASSWORD
                + "\",\"name\":\" Smoke User \"}");

    assertThat(response.status()).isEqualTo(201);
    assertThat(response.body())
        .contains("\"userId\":", "\"email\":\"smoke.user@example.com\"")
        .doesNotContain("password", "hash", VALID_PASSWORD);

    String storedHash =
        jdbcTemplate.queryForObject(
            "SELECT password_hash FROM users WHERE email = 'smoke.user@example.com'", String.class);
    assertThat(storedHash).startsWith("$2").isNotEqualTo(VALID_PASSWORD);
    assertThat(passwordHasher.matches(VALID_PASSWORD, storedHash)).isTrue();
  }

  @Test
  @DisplayName("POST /api/v1/auth/register with invalid fields -> 400 validation problem")
  void returnsFieldLevelValidationProblemsOverRealHttp() throws Exception {
    Response response = register("{\"email\":\"invalid\",\"password\":\"short\",\"name\":\"Person\"}");

    assertThat(response.status()).isEqualTo(400);
    assertThat(response.body())
        .contains("urn:sanigest:problem:validation", "\"email\"", "\"password\"");
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM users", Integer.class)).isZero();
  }

  @Test
  @DisplayName("POST duplicate normalized email -> 409 and keeps one user")
  void returnsConflictForTheSameNormalizedEmailOverRealHttp() throws Exception {
    assertThat(
            register(
                    "{\"email\":\"person@example.com\",\"password\":\""
                        + VALID_PASSWORD
                        + "\",\"name\":\"First\"}")
                .status())
        .isEqualTo(201);

    Response duplicate =
        register(
            "{\"email\":\"PERSON@EXAMPLE.COM\",\"password\":\""
                + ANOTHER_PASSWORD
                + "\",\"name\":\"Second\"}");

    assertThat(duplicate.status()).isEqualTo(409);
    assertThat(duplicate.body()).contains("urn:sanigest:problem:duplicate-email");
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM users", Integer.class)).isOne();
  }

  @Test
  @DisplayName("Concurrent POST registrations -> exactly one 201 and one 409")
  void allowsOnlyOneWinnerForConcurrentRegistrations() {
    CompletableFuture<Response> first =
        registerAsync(
            "{\"email\":\"race@example.com\",\"password\":\""
                + VALID_PASSWORD
                + "\",\"name\":\"First\"}");
    CompletableFuture<Response> second =
        registerAsync(
            "{\"email\":\"RACE@EXAMPLE.COM\",\"password\":\""
                + ANOTHER_PASSWORD
                + "\",\"name\":\"Second\"}");

    List<Integer> statuses =
        List.of(first.join().status(), second.join().status()).stream().sorted().toList();
    assertThat(statuses).containsExactly(201, 409);
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM users", Integer.class)).isOne();
  }

  private Response register(String json) throws Exception {
    HttpResponse<String> response =
        httpClient.send(request(json), HttpResponse.BodyHandlers.ofString());
    return new Response(response.statusCode(), response.body());
  }

  private CompletableFuture<Response> registerAsync(String json) {
    return httpClient
        .sendAsync(request(json), HttpResponse.BodyHandlers.ofString())
        .thenApplyAsync(response -> new Response(response.statusCode(), response.body()));
  }

  private HttpRequest request(String json) {
    return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/auth/register"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(json))
        .build();
  }

  static final class SmokeScenarioReporter implements TestWatcher {

    @Override
    public void testSuccessful(ExtensionContext context) {
      System.out.println("[SMOKE PASSED] " + context.getDisplayName());
    }

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
      System.out.println("[SMOKE FAILED] " + context.getDisplayName() + " | " + cause.getMessage());
    }
  }

  private record Response(int status, String body) {}
}
