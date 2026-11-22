package com.sanigest.identity.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthenticatedUserProfileIT {

  private static final UUID USER_ID = UUID.fromString("a1e6b198-89bd-420c-84dd-8694b6354469");
  private static final UUID OTHER_USER_ID = UUID.fromString("36ea33a7-dcee-4650-a70a-3af144501d29");
  private static final String PASSWORD_HASH = "$2a$12$sensitiveHashThatMustNeverBeReturned";

  private final HttpClient http = HttpClient.newHttpClient();
  private final JsonMapper json = new JsonMapper();

  @LocalServerPort private int port;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private JwtEncoder jwtEncoder;

  @BeforeEach
  void insertUser() {
    jdbc.update("DELETE FROM users");
    jdbc.update(
        """
        INSERT INTO users
          (id, email, password_hash, name, account_type, status, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        USER_ID,
        "profile@example.com",
        PASSWORD_HASH,
        "Profile User",
        "REGULAR",
        "ACTIVE");
  }

  @Test
  void returnsTheExactSafeContractForAValidJwt() throws Exception {
    HttpResponse<String> response =
        getProfile(token(jwtEncoder, USER_ID, validFrom(), validUntil()));
    JsonNode body = json.readTree(response.body());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(body.properties()).hasSize(4);
    assertThat(body.get("id").asText()).isEqualTo(USER_ID.toString());
    assertThat(body.get("name").asText()).isEqualTo("Profile User");
    assertThat(body.get("email").asText()).isEqualTo("profile@example.com");
    assertThat(body.get("accountType").asText()).isEqualTo("REGULAR");
    assertThat(response.body())
        .doesNotContain(PASSWORD_HASH, "passwordHash", "claims", "issuer", "audience");
  }

  @Test
  @Tag("regression")
  void rejectsAMissingJwt() throws Exception {
    assertThat(getProfile(null).statusCode()).isEqualTo(401);
  }

  @Test
  @Tag("regression")
  void rejectsAnExpiredJwt() throws Exception {
    String expired =
        token(jwtEncoder, USER_ID, Instant.now().minusSeconds(120), Instant.now().minusSeconds(60));

    assertThat(getProfile(expired).statusCode()).isEqualTo(401);
  }

  @Test
  @Tag("regression")
  void rejectsAJwtWithAnInvalidSignature() throws Exception {
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    var unrelated = generator.generateKeyPair();
    JwtEncoder otherEncoder =
        NimbusJwtEncoder.withKeyPair(
                (RSAPublicKey) unrelated.getPublic(), (RSAPrivateKey) unrelated.getPrivate())
            .build();
    String invalid = token(otherEncoder, USER_ID, validFrom(), validUntil());

    assertThat(getProfile(invalid).statusCode()).isEqualTo(401);
  }

  @Test
  @Tag("regression")
  void ignoresAClientSuppliedUserIdAndReturnsOnlyTheJwtSubject() throws Exception {
    HttpResponse<String> response =
        getProfile(
            token(jwtEncoder, USER_ID, validFrom(), validUntil()), "?userId=" + OTHER_USER_ID);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body())
        .contains(USER_ID.toString())
        .doesNotContain(OTHER_USER_ID.toString());
  }

  @Test
  void rejectsAValidJwtForAnUnknownUser() throws Exception {
    assertThat(getProfile(token(jwtEncoder, OTHER_USER_ID, validFrom(), validUntil())).statusCode())
        .isEqualTo(401);
  }

  @Test
  void rejectsADisabledUser() throws Exception {
    jdbc.update("UPDATE users SET status = 'DISABLED' WHERE id = ?", USER_ID);

    assertThat(getProfile(token(jwtEncoder, USER_ID, validFrom(), validUntil())).statusCode())
        .isEqualTo(401);
  }

  @Test
  @Tag("regression")
  void internalAuthorizationUsesCurrentAccountAndOnlyTheJwtSubject() throws Exception {
    String token = token(jwtEncoder, USER_ID, validFrom(), validUntil());
    String path = "/internal/v1/account-authorization?userId=" + OTHER_USER_ID;
    assertThat(get(path, null).statusCode()).isEqualTo(401);

    var active = get(path, token);
    assertThat(active.statusCode()).isEqualTo(204);
    assertThat(active.headers().firstValue("X-Account-Type")).contains("REGULAR");
    assertThat(active.body()).isEmpty();

    jdbc.update("UPDATE users SET account_type = 'ADVISOR' WHERE id = ?", USER_ID);
    assertThat(get(path, token).headers().firstValue("X-Account-Type")).contains("ADVISOR");

    jdbc.update("UPDATE users SET status = 'DISABLED' WHERE id = ?", USER_ID);
    var disabled = get(path, token);
    assertThat(disabled.statusCode()).isEqualTo(403);
    assertThat(disabled.headers().firstValue("X-Account-Type")).isEmpty();
  }

  @Test
  @Tag("regression")
  void keepsAdvisorEligibilityClosedWithoutTechnicalConfiguration() throws Exception {
    String path = "/internal/v1/accounts/" + OTHER_USER_ID + "/advisor-eligibility";
    assertThat(get(path, null).statusCode()).isEqualTo(401);
    assertThat(get(path, token(jwtEncoder, USER_ID, validFrom(), validUntil())).statusCode())
        .isEqualTo(403);
    assertThat(get(path, new CompanyServiceTestTokens().validToken()).statusCode()).isEqualTo(401);
  }

  private HttpResponse<String> getProfile(String token) throws Exception {
    return getProfile(token, "");
  }

  private HttpResponse<String> getProfile(String token, String query) throws Exception {
    return get("/api/v1/auth/me" + query, token);
  }

  private HttpResponse<String> get(String path, String token) throws Exception {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET();
    if (token != null) {
      request.header("Authorization", "Bearer " + token);
    }
    return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private static String token(
      JwtEncoder encoder, UUID subject, Instant issuedAt, Instant expiresAt) {
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .subject(subject.toString())
            .issuer("sanigest-test")
            .audience(List.of("sanigest-api-test"))
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .build();
    return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
  }

  private static Instant validFrom() {
    return Instant.now().minusSeconds(5);
  }

  private static Instant validUntil() {
    return Instant.now().plusSeconds(300);
  }
}
