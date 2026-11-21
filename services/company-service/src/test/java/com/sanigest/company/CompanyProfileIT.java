package com.sanigest.company;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Tag("regression")
@ActiveProfiles("test")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CompanyProfileIT {
  private static final String COMPANIES = "/api/v1/companies";
  private static final String ME = COMPANIES + "/me";

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17.7-alpine")
          .withDatabaseName("sanigest_company")
          .withUsername("sanigest_company")
          .withPassword("test-only-password");

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    properties.add("spring.datasource.username", POSTGRES::getUsername);
    properties.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @LocalServerPort private int port;
  @Autowired private JdbcTemplate jdbc;

  @Value("${sanigest.jwt.issuer}")
  private String issuer;

  @Value("${sanigest.jwt.audience}")
  private String audience;

  @Value("${sanigest.jwt.secret}")
  private String secret;

  private final HttpClient http = HttpClient.newHttpClient();

  @Test
  @Tag("smoke")
  void createsAnOwnerAndReturnsOnlyTheCompanyOfEachAuthenticatedUser() throws Exception {
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    String firstToken = token(first);
    String secondToken = token(second);
    UUID firstCompany = createCompany(first, firstToken, "Clinica Primera", "123456781");
    UUID secondCompany = createCompany(second, secondToken, "Clinica Segunda", "123456782");

    assertProfile(firstToken, firstCompany, "Clinica Primera", "123456781", second, secondCompany);
    assertProfile(secondToken, secondCompany, "Clinica Segunda", "123456782", first, firstCompany);
  }

  @Test
  void distinguishesMissingMembershipFromRevokedAuthorization() throws Exception {
    UUID userId = UUID.randomUUID();
    String token = token(userId);
    assertThat(get(ME, token).statusCode()).isEqualTo(404);

    createCompany(userId, token, "Clinica Revocada", "123456783");
    jdbc.update("UPDATE company_memberships SET status = 'REVOKED' WHERE user_id = ?", userId);

    var response = get(ME, token);
    assertThat(response.statusCode()).isEqualTo(403);
    assertThat(response.body()).doesNotContain("Clinica Revocada", userId.toString());
  }

  @Test
  void rejectsMissingExpiredAndInvalidJwt() throws Exception {
    var userId = UUID.randomUUID().toString();
    var expired = Instant.now().minusSeconds(5);
    var future = Instant.now().plusSeconds(300);

    assertThat(get(ME, null).statusCode()).isEqualTo(401);
    assertThat(get(ME, "invalid-token").statusCode()).isEqualTo(401);
    assertThat(get(ME, token(userId, expired, secret)).statusCode()).isEqualTo(401);
    assertThat(get(ME, token(userId, future, secret + "-wrong")).statusCode()).isEqualTo(401);
    assertThat(get(ME, token("invalid-subject", future, secret)).statusCode()).isEqualTo(401);
    assertThat(get(ME, token(userId, null, secret)).statusCode()).isEqualTo(401);
  }

  private UUID createCompany(UUID userId, String token, String name, String rnc) throws Exception {
    var request =
        request(COMPANIES)
            .header("Authorization", "Bearer " + token)
            .header("Idempotency-Key", userId.toString())
            .header("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    """
                    {"companyName":"%s SRL","businessName":"%s","rnc":"%s",
                     "email":"company@example.com","phone":"8095555555","location":"Santo Domingo"}
                    """
                        .formatted(name, name, rnc)))
            .build();
    var response = http.send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(201);
    return jdbc.queryForObject(
        "SELECT company_id FROM company_memberships WHERE user_id = ? AND role = 'OWNER' AND status = 'ACTIVE'",
        UUID.class,
        userId);
  }

  private void assertProfile(
      String token, UUID company, String name, String rnc, UUID otherUser, UUID otherCompany)
      throws Exception {
    var response = get(ME + "?userId=" + otherUser + "&companyId=" + otherCompany, token);
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Content-Type")).contains("application/json");
    JSONAssert.assertEquals(
        """
        {"companyId":"%s","companyName":"%s SRL","businessName":"%s","rnc":"%s",
         "location":"Santo Domingo","currency":"DOP","status":"ACTIVE"}
        """
            .formatted(company, name, name, rnc),
        response.body(),
        true);
  }

  private String token(UUID userId) throws JOSEException {
    return token(userId.toString(), Instant.now().plusSeconds(300), secret);
  }

  private String token(String subject, Instant expires, String signingKey) throws JOSEException {
    var claims = new JWTClaimsSet.Builder().subject(subject).issuer(issuer).audience(audience);
    if (expires != null) {
      claims.expirationTime(Date.from(expires));
    }
    var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
    jwt.sign(new MACSigner(signingKey.getBytes(StandardCharsets.UTF_8)));
    return jwt.serialize();
  }

  private HttpResponse<String> get(String path, String token) throws Exception {
    var request = request(path);
    if (token != null) {
      request.header("Authorization", "Bearer " + token);
    }
    return http.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
  }

  private HttpRequest.Builder request(String path) {
    return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
  }
}
