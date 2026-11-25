package com.sanigest.identity.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.sanigest.identity.application.port.out.security.PasswordHasher;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
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
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
@ActiveProfiles("local")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(IdentityRegistrationSmokeIT.SmokeScenarioReporter.class)
@Import(IdentityRegistrationSmokeIT.ProtectedProbe.class)
class IdentityRegistrationSmokeIT {

  private static final CompanyServiceTestTokens COMPANY = new CompanyServiceTestTokens();
  private static final CompanyServiceTestTokens DIRECTORY = new CompanyServiceTestTokens();
  private static final String DIRECTORY_ISSUER = "sanigest-company-directory-test";
  private static final String DIRECTORY_AUDIENCE = "sanigest-identity-member-directory";
  private static final String DIRECTORY_SCOPE = "identity:member-directory:read";
  private static final String DB_PASSWORD = TestCredentials.random();
  private static final String VALID_PASSWORD = TestCredentials.random();
  private static final String ANOTHER_PASSWORD = TestCredentials.random();

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17.7-alpine")
          .withDatabaseName("sanigest_identity")
          .withUsername("sanigest_identity")
          .withPassword(DB_PASSWORD)
          .withEnv("POSTGRES_PASSWORD", DB_PASSWORD);

  private final HttpClient httpClient = HttpClient.newHttpClient();

  @LocalServerPort private int port;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PasswordHasher passwordHasher;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    COMPANY.configure(registry);
    DIRECTORY.configure(registry, "sanigest.member-directory", DIRECTORY_ISSUER);
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("sanigest.jwt.issuer", () -> "sanigest-smoke");
    registry.add("sanigest.jwt.audience", () -> "sanigest-api-smoke");
  }

  @BeforeEach
  void cleanUsers() {
    jdbcTemplate.update("DELETE FROM refresh_tokens");
    jdbcTemplate.update("DELETE FROM users");
  }

  @Test
  @Tag("smoke")
  @DisplayName("POST /api/v1/auth/register -> 201 and persists a BCrypt hash in PostgreSQL")
  void registersThroughRealHttpAndPersistsOnlyBcryptInPostgresql() throws Exception {
    Response response =
        register(registerPayload(" Smoke.User@Example.COM ", VALID_PASSWORD, " Smoke User "));

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
  @Tag("smoke")
  @DisplayName("POST /api/v1/auth/register with invalid fields -> 400 validation problem")
  void returnsFieldLevelValidationProblemsOverRealHttp() throws Exception {
    Response response = register(registerPayload("invalid", "short", "Person"));

    assertThat(response.status()).isEqualTo(400);
    assertThat(response.body())
        .contains("urn:sanigest:problem:validation", "\"email\"", "\"password\"");
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM users", Integer.class)).isZero();
  }

  @Test
  @Tag("smoke")
  @DisplayName("POST duplicate normalized email -> 409 and keeps one user")
  void returnsConflictForTheSameNormalizedEmailOverRealHttp() throws Exception {
    assertThat(register(registerPayload("person@example.com", VALID_PASSWORD, "First")).status())
        .isEqualTo(201);

    Response duplicate =
        register(registerPayload("PERSON@EXAMPLE.COM", ANOTHER_PASSWORD, "Second"));

    assertThat(duplicate.status()).isEqualTo(409);
    assertThat(duplicate.body()).contains("urn:sanigest:problem:duplicate-email");
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM users", Integer.class)).isOne();
  }

  @Test
  @Tag("smoke")
  @DisplayName("Concurrent POST registrations -> exactly one 201 and one 409")
  void allowsOnlyOneWinnerForConcurrentRegistrations() {
    CompletableFuture<Response> first =
        registerAsync(registerPayload("race@example.com", VALID_PASSWORD, "First"));
    CompletableFuture<Response> second =
        registerAsync(registerPayload("RACE@EXAMPLE.COM", ANOTHER_PASSWORD, "Second"));

    CompletableFuture.allOf(first, second).join();
    List<Integer> statuses =
        List.of(first.join().status(), second.join().status()).stream().sorted().toList();
    assertThat(statuses).containsExactly(201, 409);
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM users", Integer.class)).isOne();
  }

  @Test
  @Tag("smoke")
  @DisplayName("Real login -> JWT grants access to a protected route")
  void logsInAndAccessesAProtectedRouteOverRealHttp() throws Exception {
    assertThat(register(registerPayload("login@example.com", VALID_PASSWORD, "Login")).status())
        .isEqualTo(201);
    HttpResponse<String> login = sendLogin("login@example.com", VALID_PASSWORD);
    String token = login.body().replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");

    HttpResponse<String> protectedResponse =
        httpClient.send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/smoke/protected"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());

    assertThat(login.statusCode()).isEqualTo(200);
    assertThat(protectedResponse.statusCode()).isEqualTo(200);
    assertThat(protectedResponse.body()).isEqualTo("protected");
  }

  @Test
  @Tag("smoke")
  @DisplayName("Real JWT authentication -> GET /api/v1/auth/me returns the profile")
  void authenticatesAndReturnsTheProfileOverRealHttp() throws Exception {
    assertThat(register(registerPayload("me@example.com", VALID_PASSWORD, "My Profile")).status())
        .isEqualTo(201);
    HttpResponse<String> login = sendLogin("me@example.com", VALID_PASSWORD);
    String token = login.body().replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");

    HttpResponse<String> profile =
        httpClient.send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/auth/me"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());

    assertThat(profile.statusCode()).isEqualTo(200);
    assertThat(profile.body())
        .contains(
            "\"name\":\"My Profile\"",
            "\"email\":\"me@example.com\"",
            "\"accountType\":\"REGULAR\"")
        .doesNotContain("password", "hash");
  }

  @Test
  @Tag("smoke")
  void rotatesRefreshTokensAndRevokesTheSessionOnLogoutOverHttp() throws Exception {
    String email = "session@example.com";
    assertThat(register(registerPayload(email, VALID_PASSWORD, "Session")).status()).isEqualTo(201);
    var json = JsonMapper.builder().build();
    var login = sendLogin(email, VALID_PASSWORD);
    assertThat(login.statusCode()).isEqualTo(200);
    String refreshToken = json.readTree(login.body()).path("refreshToken").stringValue();
    assertThat(refreshToken).isNotBlank();

    var refreshed = sessionCommand("refresh", refreshToken);
    assertThat(refreshed.statusCode()).isEqualTo(200);
    String rotated = json.readTree(refreshed.body()).path("refreshToken").stringValue();
    assertThat(rotated).isNotBlank().isNotEqualTo(refreshToken);
    assertThat(sessionCommand("logout", rotated).statusCode()).isEqualTo(204);
    assertThat(sessionCommand("refresh", rotated).statusCode()).isEqualTo(401);
  }

  @Test
  @Tag("regression")
  @DisplayName("Company alone can check advisor status against PostgreSQL")
  void checksAdvisorEligibilityOverHttpWithCompanyCredentials() throws Exception {
    String advisorEmail = "advisor-eligibility@example.com";
    String regularEmail = "regular-eligibility@example.com";
    var json = JsonMapper.builder().build();
    var advisor = register(registerPayload(advisorEmail, VALID_PASSWORD, "Advisor Fixture"));
    var regular = register(registerPayload(regularEmail, VALID_PASSWORD, "Regular Fixture"));
    assertThat(advisor.status()).isEqualTo(201);
    assertThat(regular.status()).isEqualTo(201);
    UUID advisorId = UUID.fromString(json.readTree(advisor.body()).path("userId").asText());
    UUID regularId = UUID.fromString(json.readTree(regular.body()).path("userId").asText());
    jdbcTemplate.update("UPDATE users SET account_type = 'ADVISOR' WHERE id = ?", advisorId);

    assertEligibility(advisorId, true);
    assertEligibility(regularId, false);
    assertEligibility(UUID.randomUUID(), false);

    var advisorLogin = sendLogin(advisorEmail, VALID_PASSWORD);
    var regularLogin = sendLogin(regularEmail, VALID_PASSWORD);
    assertThat(advisorLogin.statusCode()).isEqualTo(200);
    assertThat(regularLogin.statusCode()).isEqualTo(200);
    String advisorToken = json.readTree(advisorLogin.body()).path("accessToken").asText();
    String regularToken = json.readTree(regularLogin.body()).path("accessToken").asText();
    assertThat(getEligibility(regularId, advisorToken).statusCode()).isEqualTo(403);
    assertThat(getEligibility(advisorId, regularToken).statusCode()).isEqualTo(403);

    jdbcTemplate.update("UPDATE users SET status = 'DISABLED' WHERE id = ?", advisorId);
    assertEligibility(advisorId, false);
  }

  @Test
  @Tag("smoke")
  void directoryReturnsOnlyRegisteredIdsAndKeepsEmailUnverified() throws Exception {
    var json = JsonMapper.builder().build();
    String email = "directory@example.com";
    var registered = register(registerPayload(email, VALID_PASSWORD, "Registered Name"));
    assertThat(registered.status()).isEqualTo(201);
    String id = json.readTree(registered.body()).path("userId").asString();
    assertThat(
            register(registerPayload("other-directory@example.com", VALID_PASSWORD, "Other"))
                .status())
        .isEqualTo(201);
    String body =
        json.writeValueAsString(
            java.util.Map.of("userIds", List.of(id, UUID.randomUUID().toString())));
    var response = directory(body, directoryAssertion(DIRECTORY_SCOPE));
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
    var payload = json.readTree(response.body());
    assertThat(payload.size()).isEqualTo(1);
    assertThat(payload.get(0))
        .isEqualTo(
            json.valueToTree(
                java.util.Map.of(
                    "userId",
                    id,
                    "name",
                    "Registered Name",
                    "email",
                    email,
                    "emailVerified",
                    false)));
    String userToken =
        json.readTree(sendLogin(email, VALID_PASSWORD).body()).path("accessToken").asString();
    assertThat(directory(body, userToken).statusCode()).isEqualTo(403);
    assertThat(directory(body, COMPANY.validToken()).statusCode()).isEqualTo(401);
    assertThat(directory(body, directoryAssertion("wrong:scope")).statusCode()).isEqualTo(403);
    String oversized =
        json.writeValueAsString(
            java.util.Map.of("userIds", java.util.Collections.nCopies(101, id)));
    assertThat(directory(oversized, directoryAssertion(DIRECTORY_SCOPE)).statusCode())
        .isEqualTo(400);
  }

  private static String directoryAssertion(String scope) {
    return DIRECTORY.issue(
        CompanyServiceTestTokens.claims()
            .issuer(DIRECTORY_ISSUER)
            .audience(List.of(DIRECTORY_AUDIENCE))
            .claim("scope", scope),
        CompanyServiceTestTokens.TYPE);
  }

  private HttpResponse<String> directory(String body, String token) throws Exception {
    return httpClient.send(
        HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + "/internal/v1/member-directory"))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private void assertEligibility(UUID userId, boolean eligible) throws Exception {
    var response = getEligibility(userId, COMPANY.validToken());
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isEqualTo("{\"eligible\":" + eligible + "}");
    assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
  }

  private HttpResponse<String> getEligibility(UUID userId, String token) throws Exception {
    String url =
        "http://127.0.0.1:" + port + "/internal/v1/accounts/" + userId + "/advisor-eligibility";
    return httpClient.send(
        HttpRequest.newBuilder(URI.create(url))
            .header("Authorization", "Bearer " + token)
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> sessionCommand(String operation, String refreshToken)
      throws Exception {
    return httpClient.send(
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/auth/" + operation))
            .header("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString("{\"refreshToken\":\"" + refreshToken + "\"}"))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> sendLogin(String email, String password) throws Exception {
    String json = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password);
    return httpClient.send(
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/auth/login"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private Response register(String json) throws Exception {
    HttpResponse<String> response =
        httpClient.send(request(json), HttpResponse.BodyHandlers.ofString());
    return new Response(response.statusCode(), response.body());
  }

  private CompletableFuture<Response> registerAsync(String json) {
    return httpClient
        .sendAsync(request(json), HttpResponse.BodyHandlers.ofString())
        .thenApply(response -> new Response(response.statusCode(), response.body()));
  }

  private HttpRequest request(String json) {
    return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/auth/register"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(json))
        .build();
  }

  private static String registerPayload(String email, String pw, String name) {
    return String.format(
        "{\"email\":\"%s\",\"%s\":\"%s\",\"name\":\"%s\"}", email, "password", pw, name);
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

  @RestController
  static class ProtectedProbe {

    @GetMapping("/smoke/protected")
    String protectedRoute() {
      return "protected";
    }
  }
}
