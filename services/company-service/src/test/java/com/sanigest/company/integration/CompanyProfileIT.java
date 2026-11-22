package com.sanigest.company.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sanigest.company.application.exception.CompanyAccessDeniedException;
import com.sanigest.company.application.port.out.integration.InvitationDelivery;
import com.sanigest.company.application.port.out.persistence.MembershipRepository;
import com.sanigest.company.application.usecase.advisor.CheckAdvisorPermissionUseCase;
import com.sanigest.company.application.usecase.advisor.RevokeAdvisorAccessUseCase;
import com.sanigest.company.application.usecase.team.ChangeCompanyMemberRoleUseCase;
import com.sanigest.company.domain.enums.CompanyRole;
import com.sanigest.company.domain.model.AdvisorPermission;
import com.sanigest.company.domain.model.AdvisorPermission.Module;
import com.sanigest.company.domain.model.AdvisorPermission.Scope;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.skyscreamer.jsonassert.JSONAssert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Tag("regression")
@ActiveProfiles("test")
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CompanyProfileIT {
  private static final String COMPANIES = "/api/v1/companies";
  private static final String ME = COMPANIES + "/me";
  private static final String AUDIT = ME + "/audit-log";
  private static final String ADVISOR_COMPANIES = "/api/v1/advisor/companies";
  private static final String ADVISOR_ACCESS = ME + "/advisor-access";
  private static final String SERVICE_ISSUER = "sanigest-company-eligibility-test";
  private static final AdvisorPermission ADVISOR_READ =
      new AdvisorPermission(Module.CATALOG, Scope.READ);
  private static final AdvisorPermission ADVISOR_WRITE =
      new AdvisorPermission(Module.CATALOG, Scope.WRITE);
  private static final KeyPair SERVICE_KEYS = serviceKeys();
  private static final Path SERVICE_KEY_FILE = serviceKeyFile();
  private static final ConcurrentHashMap<UUID, String> ADVISOR_ACCOUNTS = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<UUID, BlockEligibility> BLOCKED_ELIGIBILITY =
      new ConcurrentHashMap<>();

  private static final ConcurrentHashMap<String, Integer> ACCOUNT_STATUS =
      new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<String, String> ACCOUNT_TYPE = new ConcurrentHashMap<>();
  private static final java.util.concurrent.ExecutorService IDENTITY_THREADS =
      Executors.newVirtualThreadPerTaskExecutor();
  private static final HttpServer IDENTITY = identity();
  private static final ConcurrentHashMap<String, String> DELIVERIES = new ConcurrentHashMap<>();

  @TestConfiguration
  static class DeliveryConfiguration {
    @Bean
    InvitationDelivery testDelivery() {
      return (recipient, token, expiresAt) -> DELIVERIES.put(recipient, token);
    }
  }

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17.7-alpine")
          .withDatabaseName("sanigest_company")
          .withUsername("sanigest_company")
          .withPassword("test-only-password");

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry properties) {
    properties.add(
        "sanigest.identity-url", () -> "http://127.0.0.1:" + IDENTITY.getAddress().getPort());
    properties.add("sanigest.advisor-eligibility.issuer", () -> SERVICE_ISSUER);
    properties.add(
        "sanigest.advisor-eligibility.private-key-location",
        () -> SERVICE_KEY_FILE.toUri().toString());
    properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    properties.add("spring.datasource.username", POSTGRES::getUsername);
    properties.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @AfterAll
  static void stopIdentity() throws IOException {
    IDENTITY.stop(0);
    IDENTITY_THREADS.close();
    Files.deleteIfExists(SERVICE_KEY_FILE);
  }

  private static HttpServer identity() {
    try {
      var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext(
          "/internal/v1/account-authorization",
          exchange -> {
            try (exchange) {
              String token = exchange.getRequestHeaders().getFirst("Authorization").substring(7);
              String subject = SignedJWT.parse(token).getJWTClaimsSet().getSubject();
              int status = ACCOUNT_STATUS.getOrDefault(subject, 204);
              if (status == 204) {
                exchange
                    .getResponseHeaders()
                    .set("X-Account-Type", ACCOUNT_TYPE.getOrDefault(subject, "REGULAR"));
              }
              exchange.sendResponseHeaders(status, -1);
            } catch (ParseException invalid) {
              throw new IOException(invalid);
            }
          });
      server.createContext("/internal/v1/accounts/", CompanyProfileIT::eligibility);
      server.setExecutor(IDENTITY_THREADS);
      server.start();
      return server;
    } catch (IOException failure) {
      throw new IllegalStateException(failure);
    }
  }

  @LocalServerPort private int port;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private CheckAdvisorPermissionUseCase advisorPermissions;

  @Autowired private RevokeAdvisorAccessUseCase revokeAdvisorAccess;

  @Autowired private ChangeCompanyMemberRoleUseCase changeMemberRole;
  @Autowired private MembershipRepository memberships;
  @Autowired private PlatformTransactionManager transactionManager;

  @Value("${sanigest.jwt.issuer}")
  private String issuer;

  @Value("${sanigest.jwt.audience}")
  private String audience;

  private final RSAPrivateKey signingKey = signingKey();

  private final HttpClient http = HttpClient.newHttpClient();

  @Test
  @Tag("smoke")
  void grantsListsAndRevokesOnlyWithinTheOwnersCompany() throws Exception {
    var owner = newOwner("Advisor Smoke");
    var other = newOwner("Advisor Other");
    UUID advisor = eligibleAdvisor();
    String body = advisorBody(advisor, 0, "{\"module\":\"CATALOG\"}");
    String spoofedBody = body.replaceFirst("\\{", "{\"companyId\":\"" + other.companyId() + "\",");
    var grant = sendAdvisor(advisorPut(owner.token(), spoofedBody));
    assertThat(grant.statusCode()).isEqualTo(200);
    assertThat(grant.body()).contains("\"scope\":\"READ\"", "\"revision\":1");
    assertThat(get(ADVISOR_ACCESS, owner.token()).body()).contains(advisor.toString());
    assertSelector(
        get(ADVISOR_COMPANIES, token(advisor)),
        "[%s]"
            .formatted(
                selectorCompany(
                    owner,
                    "Advisor Smoke",
                    """
                    {"module":"CATALOG","scope":"READ"}
                    """)));
    assertThat(get(ADVISOR_ACCESS + "?companyId=" + owner.companyId(), other.token()).body())
        .isEqualTo("[]");
    advisorPermissions.require(advisor, owner.companyId(), ADVISOR_READ);
    assertThatThrownBy(() -> advisorPermissions.require(advisor, owner.companyId(), ADVISOR_WRITE))
        .isInstanceOf(CompanyAccessDeniedException.class);
    assertThatThrownBy(() -> advisorPermissions.require(advisor, other.companyId(), ADVISOR_READ))
        .isInstanceOf(CompanyAccessDeniedException.class);
    assertThat(
            sendAdvisor(
                    advisorPut(
                        other.token(),
                        advisorBody(advisor, 0, "{\"module\":\"CATALOG\",\"scope\":\"WRITE\"}")))
                .statusCode())
        .isEqualTo(200);
    advisorPermissions.require(advisor, other.companyId(), ADVISOR_WRITE);
    assertThat(sendAdvisor(advisorDelete(owner.token(), advisor)).statusCode()).isEqualTo(204);
    assertThatThrownBy(() -> advisorPermissions.require(advisor, owner.companyId(), ADVISOR_READ))
        .isInstanceOf(CompanyAccessDeniedException.class);
    advisorPermissions.require(advisor, other.companyId(), ADVISOR_WRITE);
    assertSelector(
        get(ADVISOR_COMPANIES, token(advisor)),
        "[%s]"
            .formatted(
                selectorCompany(
                    other,
                    "Advisor Other",
                    """
                    {"module":"CATALOG","scope":"WRITE"}
                    """)));
    assertThat(sendAdvisor(advisorDelete(other.token(), advisor)).statusCode()).isEqualTo(204);
    assertSelector(get(ADVISOR_COMPANIES, token(advisor)), "[]");
    assertThat(membershipCount(advisor)).isZero();
    var audit = get(AUDIT, owner.token());
    assertThat(audit.body())
        .contains(
            "ADVISOR_GRANTED",
            "ADVISOR_REVOKED",
            advisor.toString(),
            owner.companyId().toString(),
            "\"scope\":\"READ\"");
  }

  @Test
  void selectorFiltersPermissionsAndCompaniesAndIgnoresUntrustedContext() throws Exception {
    var alpha = newOwner("Selector Alpha");
    var beta = newOwner("Selector Beta");
    var unrelated = newOwner("Selector Unrelated");
    UUID advisor = eligibleAdvisor();
    UUID otherAdvisor = eligibleAdvisor();
    String jwt = token(advisor);
    assertSelector(get(ADVISOR_COMPANIES, jwt), "[]");

    String read =
        """
        {"module":"CATALOG","scope":"READ"}
        """;
    String write =
        """
        {"module":"CATALOG","scope":"WRITE"}
        """;
    String sales =
        """
        {"module":"SALES","scope":"READ"}
        """;
    assertThat(sendAdvisor(advisorPut(beta.token(), advisorBody(advisor, 0, sales))).statusCode())
        .isEqualTo(200);
    assertThat(
            sendAdvisor(
                    advisorPut(
                        alpha.token(), advisorBody(advisor, 0, sales + "," + write + "," + read)))
                .statusCode())
        .isEqualTo(200);
    assertThat(
            sendAdvisor(advisorPut(unrelated.token(), advisorBody(otherAdvisor, 0, read)))
                .statusCode())
        .isEqualTo(200);

    var spoofed =
        request(
                ADVISOR_COMPANIES
                    + "?advisorId="
                    + otherAdvisor
                    + "&companyId="
                    + unrelated.companyId())
            .header("Authorization", "Bearer " + jwt)
            .header("X-Company-Id", unrelated.companyId().toString())
            .header("X-Advisor-Id", otherAdvisor.toString())
            .GET()
            .build();
    assertSelector(
        sendAdvisor(spoofed),
        "[%s,%s]"
            .formatted(
                selectorCompany(alpha, "Selector Alpha", read + "," + write + "," + sales),
                selectorCompany(beta, "Selector Beta", sales)));
    assertSelector(
        get(ADVISOR_COMPANIES, token(otherAdvisor)),
        "[%s]".formatted(selectorCompany(unrelated, "Selector Unrelated", read)));
    assertThatThrownBy(
            () -> advisorPermissions.require(advisor, unrelated.companyId(), ADVISOR_READ))
        .isInstanceOf(CompanyAccessDeniedException.class);

    assertThat(sendAdvisor(advisorPut(alpha.token(), advisorBody(advisor, 1, write))).statusCode())
        .isEqualTo(200);
    jdbc.update("UPDATE companies SET status = 'INACTIVE' WHERE id = ?", beta.companyId());
    assertSelector(
        get(ADVISOR_COMPANIES, jwt),
        "[%s]".formatted(selectorCompany(alpha, "Selector Alpha", write)));
  }

  @Test
  void selectorRequiresAUserJwtAndCurrentIdentityEligibility() throws Exception {
    assertThat(get(ADVISOR_COMPANIES, null).statusCode()).isEqualTo(401);
    assertThat(get(ADVISOR_COMPANIES, "not-a-jwt").statusCode()).isEqualTo(401);
    UUID advisor = eligibleAdvisor();
    String expired = token(advisor.toString(), Instant.now().minusSeconds(5), signingKey);
    assertThat(get(ADVISOR_COMPANIES, expired).statusCode()).isEqualTo(401);

    var service =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(new com.nimbusds.jose.JOSEObjectType("sanigest-service+jwt"))
                .build(),
            new JWTClaimsSet.Builder()
                .issuer(SERVICE_ISSUER)
                .subject("company-service")
                .audience("sanigest-identity-advisor-eligibility")
                .claim("scope", "identity:advisor-eligibility:read")
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(60)))
                .build());
    service.sign(new RSASSASigner((RSAPrivateKey) SERVICE_KEYS.getPrivate()));
    assertThat(get(ADVISOR_COMPANIES, service.serialize()).statusCode()).isEqualTo(401);

    // Even a signed accountType claim cannot override Identity.
    var user = SignedJWT.parse(token(advisor));
    var claimedAdvisor =
        new SignedJWT(
            user.getHeader(),
            new JWTClaimsSet.Builder(user.getJWTClaimsSet())
                .claim("accountType", "ADVISOR")
                .build());
    claimedAdvisor.sign(new RSASSASigner(signingKey));
    for (String account : List.of("REGULAR", "INACTIVE_ADVISOR", "MISSING", "UNAVAILABLE")) {
      ADVISOR_ACCOUNTS.put(advisor, account);
      var denied = get(ADVISOR_COMPANIES, claimedAdvisor.serialize());
      assertThat(denied.statusCode())
          .as(account)
          .isEqualTo("UNAVAILABLE".equals(account) ? 503 : 403);
      assertThat(denied.headers().firstValue("Cache-Control"))
          .hasValueSatisfying(value -> assertThat(value).contains("no-store"));
    }
  }

  private static String selectorCompany(AdvisorOwner owner, String name, String permissions) {
    return """
    {"companyId":"%s","companyName":"%s SRL","permissions":[%s]}
    """
        .formatted(owner.companyId(), name, permissions);
  }

  private static void assertSelector(HttpResponse<String> response, String expected)
      throws Exception {
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
    JSONAssert.assertEquals(expected, response.body(), true);
  }

  @Test
  void rejectsUnauthorizedActorsInvalidPermissionsAndUnverifiableRecipients() throws Exception {
    var owner = newOwner("Advisor Security");
    UUID advisor = eligibleAdvisor();
    String read = advisorBody(advisor, 0, "{\"module\":\"CATALOG\"}");
    assertThat(get(ADVISOR_ACCESS, null).statusCode()).isEqualTo(401);
    assertThat(get(ADVISOR_ACCESS, "invalid").statusCode()).isEqualTo(401);
    for (String role : List.of("ADMIN", "OPERATOR", "VIEWER")) {
      UUID member = UUID.randomUUID();
      addMember(owner.companyId(), member, role);
      assertThat(sendAdvisor(advisorPut(token(member), read)).statusCode()).isEqualTo(403);
    }
    ACCOUNT_TYPE.put(advisor.toString(), "ADVISOR");
    assertThat(sendAdvisor(advisorPut(token(advisor), read)).statusCode()).isEqualTo(403);
    for (String permission :
        List.of("{\"module\":\"OWNER\"}", "{\"module\":\"CATALOG\",\"scope\":\"ADMIN\"}")) {
      assertThat(
              sendAdvisor(advisorPut(owner.token(), advisorBody(advisor, 0, permission)))
                  .statusCode())
          .isEqualTo(400);
    }
    for (String account : List.of("REGULAR", "DISABLED", "MISSING", "UNAVAILABLE", "DENIED")) {
      ADVISOR_ACCOUNTS.put(advisor, account);
      assertThat(sendAdvisor(advisorPut(owner.token(), read)).statusCode())
          .isEqualTo(List.of("UNAVAILABLE", "DENIED").contains(account) ? 503 : 403);
    }
    assertThat(get(ADVISOR_ACCESS, owner.token()).body()).isEqualTo("[]");
    assertThat(
            jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM company_audit_log
                WHERE company_id = ? AND operation = 'ADVISOR_GRANTED' AND result = 'REJECTED'
                  AND target_user_id IS NULL
                """,
                Integer.class,
                owner.companyId()))
        .isEqualTo(3);
  }

  @Test
  void timesOutWhenIdentityStallsAndDoesNotPersistAccess() throws Exception {
    var owner = newOwner("Advisor Timeout");
    UUID advisor = eligibleAdvisor();
    var delay = new BlockEligibility(new CountDownLatch(1), new CountDownLatch(1));
    BLOCKED_ELIGIBILITY.put(advisor, delay);
    try {
      var response =
          sendAdvisor(
              advisorPut(owner.token(), advisorBody(advisor, 0, "{\"module\":\"CATALOG\"}")));
      assertThat(response.statusCode()).isEqualTo(503);
      assertThat(delay.entered().getCount()).isZero();
      assertThat(get(ADVISOR_ACCESS, owner.token()).body()).isEqualTo("[]");
    } finally {
      BLOCKED_ELIGIBILITY.remove(advisor);
      delay.release().countDown();
    }
  }

  @Test
  void duplicateGrantsAndRevocationsAreIdempotentAndAuditRollsBack() throws Exception {
    var owner = newOwner("Advisor Transactions");
    UUID advisor = eligibleAdvisor();
    var put = advisorPut(owner.token(), advisorBody(advisor, 0, "{\"module\":\"CATALOG\"}"));
    assertConcurrentStatus(put, 200);
    assertThat(
            jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM company_advisor_permissions WHERE company_id = ? AND advisor_id = ?
                """,
                Integer.class,
                owner.companyId(),
                advisor))
        .isEqualTo(1);
    assertThatThrownBy(() ->
            jdbc.update(
                """
                INSERT INTO company_advisor_permissions (company_id, advisor_id, module, scope, granted_at)
                VALUES (?, ?, 'CATALOG', 'READ', CURRENT_TIMESTAMP)
                """,
                owner.companyId(),
                advisor))
        .isInstanceOf(DuplicateKeyException.class);
    var transaction = new TransactionTemplate(transactionManager);
    transaction.executeWithoutResult(status -> {
      revokeAdvisorAccess.revoke(owner.actorId(), advisor);
      status.setRollbackOnly();
    });
    advisorPermissions.require(advisor, owner.companyId(), ADVISOR_READ);
    assertThat(get(AUDIT, owner.token()).body()).doesNotContain("ADVISOR_REVOKED");
    assertConcurrentStatus(advisorDelete(owner.token(), advisor), 204);
    assertThat(
            jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM company_audit_log WHERE company_id = ? AND target_user_id = ?
                """,
                Integer.class,
                owner.companyId(),
                advisor))
        .isEqualTo(2);
  }

  @Test
  void revocationFencesAnUpdateAlreadyWaitingForIdentity() throws Exception {
    var owner = newOwner("Advisor Race");
    UUID advisor = eligibleAdvisor();
    assertThat(
            sendAdvisor(
                    advisorPut(owner.token(), advisorBody(advisor, 0, "{\"module\":\"CATALOG\"}")))
                .statusCode())
        .isEqualTo(200);
    var delay = new BlockEligibility(new CountDownLatch(1), new CountDownLatch(1));
    BLOCKED_ELIGIBILITY.put(advisor, delay);
    var stale =
        http.sendAsync(
            advisorPut(
                owner.token(),
                advisorBody(advisor, 1, "{\"module\":\"CATALOG\",\"scope\":\"WRITE\"}")),
            HttpResponse.BodyHandlers.ofString());
    try {
      assertThat(delay.entered().await(2, TimeUnit.SECONDS)).isTrue();
      assertThat(sendAdvisor(advisorDelete(owner.token(), advisor)).statusCode()).isEqualTo(204);
    } finally {
      BLOCKED_ELIGIBILITY.remove(advisor);
      delay.release().countDown();
    }
    assertThat(stale.get(5, TimeUnit.SECONDS).statusCode()).isEqualTo(409);
    assertThatThrownBy(() -> advisorPermissions.require(advisor, owner.companyId(), ADVISOR_WRITE))
        .isInstanceOf(CompanyAccessDeniedException.class);
  }

  private void assertConcurrentStatus(HttpRequest request, int status) throws Exception {
    var first = http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    var second = http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    assertThat(first.get(5, TimeUnit.SECONDS).statusCode()).isEqualTo(status);
    assertThat(second.get(5, TimeUnit.SECONDS).statusCode()).isEqualTo(status);
  }

  private AdvisorOwner newOwner(String name) throws Exception {
    UUID id = UUID.randomUUID();
    String jwt = token(id);
    return new AdvisorOwner(id, createCompany(id, jwt, name, null), jwt);
  }

  private static UUID eligibleAdvisor() {
    UUID id = UUID.randomUUID();
    ADVISOR_ACCOUNTS.put(id, "ACTIVE_ADVISOR");
    return id;
  }

  private static String advisorBody(UUID advisor, long revision, String permissions) {
    return """
    {"advisorId":"%s","revision":%d,"permissions":[%s]}
    """
        .formatted(advisor, revision, permissions);
  }

  private HttpRequest advisorPut(String jwt, String body) {
    return request(ADVISOR_ACCESS)
        .header("Authorization", "Bearer " + jwt)
        .header("Content-Type", "application/json")
        .PUT(HttpRequest.BodyPublishers.ofString(body))
        .build();
  }

  private HttpRequest advisorDelete(String jwt, UUID advisor) {
    return request(ADVISOR_ACCESS + "?advisorId=" + advisor)
        .header("Authorization", "Bearer " + jwt)
        .DELETE()
        .build();
  }

  private HttpResponse<String> sendAdvisor(HttpRequest request) throws Exception {
    return http.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private static KeyPair serviceKeys() {
    try {
      var generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (java.security.GeneralSecurityException failure) {
      throw new IllegalStateException(failure);
    }
  }

  private static Path serviceKeyFile() {
    try {
      Path file = Files.createTempFile("company-eligibility-test-", ".pem");
      String key = Base64.getMimeEncoder().encodeToString(SERVICE_KEYS.getPrivate().getEncoded());
      Files.writeString(
          file, "-----BEGIN PRIVATE KEY-----\n" + key + "\n-----END PRIVATE KEY-----\n");
      return file;
    } catch (IOException failure) {
      throw new IllegalStateException(failure);
    }
  }

  private static void eligibility(HttpExchange exchange) throws IOException {
    try (exchange) {
      if (!validServiceToken(exchange.getRequestHeaders().getFirst("Authorization"))) {
        exchange.sendResponseHeaders(403, -1);
        return;
      }
      var segments = exchange.getRequestURI().getPath().split("/");
      if (segments.length != 6
          || !"advisor-eligibility".equals(segments[5])
          || !"GET".equals(exchange.getRequestMethod())) {
        exchange.sendResponseHeaders(404, -1);
        return;
      }
      UUID advisor = UUID.fromString(segments[4]);
      var delay = BLOCKED_ELIGIBILITY.get(advisor);
      if (delay != null) {
        delay.entered().countDown();
        if (!delay.release().await(4, TimeUnit.SECONDS)) {
          exchange.sendResponseHeaders(503, -1);
          return;
        }
      }
      String account = ADVISOR_ACCOUNTS.getOrDefault(advisor, "MISSING");
      switch (account) {
        case "UNAVAILABLE" -> exchange.sendResponseHeaders(503, -1);
        case "DENIED" -> exchange.sendResponseHeaders(403, -1);
        default -> {
          byte[] body =
              ("{\"eligible\":" + "ACTIVE_ADVISOR".equals(account) + "}")
                  .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
        }
      }
    } catch (ParseException | JOSEException failure) {
      throw new IOException(failure);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IOException(interrupted);
    }
  }

  private static boolean validServiceToken(String authorization)
      throws ParseException, JOSEException {
    if (authorization == null || !authorization.startsWith("Bearer ")) {
      return false;
    }
    var jwt = SignedJWT.parse(authorization.substring(7));
    var claims = jwt.getJWTClaimsSet();
    return JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())
        && "sanigest-service+jwt".equals(String.valueOf(jwt.getHeader().getType()))
        && jwt.verify(new RSASSAVerifier((RSAPublicKey) SERVICE_KEYS.getPublic()))
        && SERVICE_ISSUER.equals(claims.getIssuer())
        && "company-service".equals(claims.getSubject())
        && List.of("sanigest-identity-advisor-eligibility").equals(claims.getAudience())
        && "identity:advisor-eligibility:read".equals(claims.getStringClaim("scope"))
        && validLifetime(claims);
  }

  private static boolean validLifetime(JWTClaimsSet claims) {
    Instant issued = claims.getIssueTime().toInstant();
    Instant expires = claims.getExpirationTime().toInstant();
    Instant now = Instant.now();
    return !issued.isAfter(now)
        && expires.isAfter(now)
        && Duration.between(issued, expires).compareTo(Duration.ofMinutes(2)) <= 0;
  }

  private record AdvisorOwner(UUID actorId, UUID companyId, String token) {}

  private record BlockEligibility(CountDownLatch entered, CountDownLatch release) {}

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

    var before = updatedAt(firstCompany);
    var response =
        patch(
            ME + "?companyId=" + secondCompany,
            firstToken,
            """
            {"businessName":"Nuevo nombre","email":"new@example.com","phone":"8095550000",
             "location":"Santiago","rnc":"12345678901"}
            """);
    assertThat(response.statusCode()).isEqualTo(200);
    var after = updatedAt(firstCompany);
    assertThat(after).isAfter(before);
    JSONAssert.assertEquals(
        """
        {"companyId":"%s","companyName":"Clinica Primera SRL","businessName":"Nuevo nombre",
         "email":"new@example.com","phone":"8095550000","location":"Santiago","rnc":"12345678901",
         "currency":"DOP","status":"ACTIVE","updatedAt":"%s"}
        """
            .formatted(firstCompany, after),
        response.body(),
        true);
    assertThat(get(ME, firstToken).body()).contains("Nuevo nombre", "12345678901", "Santiago");
    assertProfile(secondToken, secondCompany, "Clinica Segunda", "123456782", first, firstCompany);

    var partial =
        patch(
            ME,
            firstToken,
            """
            {"rnc":null}
            """);
    assertThat(partial.statusCode()).isEqualTo(200);
    assertThat(partial.body())
        .contains("\"rnc\":null", "Nuevo nombre", "new@example.com", "8095550000");
    assertThat(
            jdbc.queryForMap(
                "SELECT business_name, email, phone, location, rnc FROM companies WHERE id = ?",
                firstCompany))
        .containsEntry("business_name", "Nuevo nombre")
        .containsEntry("email", "new@example.com")
        .containsEntry("phone", "8095550000")
        .containsEntry("location", "Santiago")
        .containsEntry("rnc", null);
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
    assertThat(
            patch(
                    ME,
                    token,
                    """
                    {"location":"Santiago"}
                    """)
                .statusCode())
        .isEqualTo(403);
  }

  @Test
  void rejectsMissingExpiredAndInvalidJwt() throws Exception {
    var userId = UUID.randomUUID().toString();
    var expired = Instant.now().minusSeconds(5);
    var future = Instant.now().plusSeconds(300);

    assertThat(get(ME, null).statusCode()).isEqualTo(401);
    assertThat(get(ME, "invalid-token").statusCode()).isEqualTo(401);
    assertThat(get(ME, token(userId, expired, signingKey)).statusCode()).isEqualTo(401);
    assertThat(
            get(
                    ME,
                    token(
                        userId,
                        future,
                        (RSAPrivateKey)
                            KeyPairGenerator.getInstance("RSA").generateKeyPair().getPrivate()))
                .statusCode())
        .isEqualTo(401);
    assertThat(get(ME, token("invalid-subject", future, signingKey)).statusCode()).isEqualTo(401);
    assertThat(get(ME, token(userId, null, signingKey)).statusCode()).isEqualTo(401);
  }

  @Test
  void checksAccountStatusAndTypeForEveryOperationWithTheSameToken() throws Exception {
    UUID user = UUID.randomUUID();
    String token = token(user);
    createCompany(user, token, "Clinica Identidad", "123456790");
    assertThat(get(ME, token).statusCode()).isEqualTo(200);

    ACCOUNT_STATUS.put(user.toString(), 403);
    assertThat(get(ME, token).statusCode()).isEqualTo(403);
    assertThat(patch(ME, token, "{\"location\":\"Santiago\"}").statusCode()).isEqualTo(403);

    ACCOUNT_STATUS.put(user.toString(), 503);
    assertThat(get(ME, token).statusCode()).isEqualTo(503);
    assertThat(patch(ME, token, "{\"location\":\"Santiago\"}").statusCode()).isEqualTo(503);

    ACCOUNT_STATUS.remove(user.toString());
    ACCOUNT_TYPE.put(user.toString(), "ADVISOR");
    assertThat(get(ME, token).statusCode()).isEqualTo(403);
    assertThat(patch(ME, token, "{\"location\":\"Santiago\"}").statusCode()).isEqualTo(403);
    ACCOUNT_TYPE.remove(user.toString());
    assertThat(get(ME, token).body()).contains("Santo Domingo");
  }

  @Test
  void enforcesPatchAuthorizationAndRejectsProtectedOrInvalidFields() throws Exception {
    UUID owner = UUID.randomUUID();
    UUID admin = UUID.randomUUID();
    String ownerToken = token(owner);
    String adminToken = token(admin);
    UUID company = createCompany(owner, ownerToken, "Clinica Permisos", "123456784");
    jdbc.update(
        "INSERT INTO company_memberships (id, company_id, user_id, role) VALUES (?, ?, ?, 'ADMIN')",
        UUID.randomUUID(),
        company,
        admin);
    String payload = "{\"location\":\"Santiago\"}";

    assertThat(patch(ME, null, payload).statusCode()).isEqualTo(401);
    assertThat(patch(ME, "invalid-token", payload).statusCode()).isEqualTo(401);
    assertThat(patch(ME, token(UUID.randomUUID()), payload).statusCode()).isEqualTo(404);
    assertThat(patch(ME, adminToken, "{\"rnc\":null,\"location\":\"La Vega\"}").statusCode())
        .isEqualTo(403);
    assertThat(patch(ME, adminToken, "{\"rnc\":\"123456785\"}").statusCode()).isEqualTo(403);

    var before = updatedAt(company);
    for (String body :
        List.of(
            "{}",
            "{\"location\":null}",
            "{\"businessName\":\"  \"}",
            "{\"rnc\":\"\"}",
            "{\"rnc\":\"123\"}",
            "{\"email\":\"invalid\"}",
            "{\"phone\":8095555555}",
            "{\"companyId\":\"" + UUID.randomUUID() + "\",\"location\":\"La Vega\"}",
            "{\"currency\":\"USD\"}",
            "{\"ownerId\":null}",
            "{\"companyName\":\"Protegido\"}")) {
      assertThat(patch(ME, ownerToken, body).statusCode()).as(body).isEqualTo(400);
    }
    assertThat(updatedAt(company)).isEqualTo(before);
    assertThat(patch(ME, adminToken, payload).statusCode()).isEqualTo(200);
    jdbc.update("UPDATE company_memberships SET role = 'VIEWER' WHERE user_id = ?", admin);
    assertThat(get(ME, adminToken).statusCode()).isEqualTo(200);
    assertThat(patch(ME, adminToken, payload).statusCode()).isEqualTo(403);
    assertThat(get(ME, ownerToken).body()).contains("Santiago", "123456784");
  }

  @Test
  void concurrentOwnersCannotClaimTheSameRnc() throws Exception {
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    String firstToken = token(first);
    String secondToken = token(second);
    createCompany(first, firstToken, "Clinica Concurrente A", "123456786");
    createCompany(second, secondToken, "Clinica Concurrente B", "123456787");
    String body = "{\"rnc\":\"123456788\",\"location\":\"Santiago\"}";

    var firstPatch =
        http.sendAsync(patchRequest(ME, firstToken, body), HttpResponse.BodyHandlers.ofString());
    var secondPatch =
        http.sendAsync(patchRequest(ME, secondToken, body), HttpResponse.BodyHandlers.ofString());

    var firstStatus = firstPatch.get().statusCode();
    assertThat(List.of(firstStatus, secondPatch.get().statusCode()))
        .containsExactlyInAnyOrder(200, 409);
    String loserProfile = get(ME, firstStatus == 409 ? firstToken : secondToken).body();
    assertThat(loserProfile)
        .contains("Santo Domingo", firstStatus == 409 ? "123456786" : "123456787");
  }

  @Test
  @Tag("smoke")
  void invitationSmokeCreatesMembershipAndNeverListsAcrossCompanies(CapturedOutput output)
      throws Exception {
    UUID owner = UUID.randomUUID();
    UUID outsider = UUID.randomUUID();
    UUID guest = UUID.randomUUID();
    String ownerJwt = token(owner);
    String outsiderJwt = token(outsider);
    String email = guest + "@example.com";
    String guestJwt = verifiedToken(guest, email, true);
    UUID company = createCompany(owner, ownerJwt, "Inviting clinic", "123456791");
    UUID otherCompany = createCompany(outsider, outsiderJwt, "Other clinic", "123456792");

    var created = invite(ownerJwt, email, "VIEWER", false);
    assertThat(created.statusCode()).isEqualTo(201);
    String invitationToken = DELIVERIES.remove(email);
    assertThat(invitationToken).matches("[A-Za-z0-9_-]{43}");
    assertThat(created.body()).contains("VIEWER", "PENDING").doesNotContain(invitationToken);
    assertThat(
            jdbc.queryForMap(
                "SELECT company_id, token_hash, status FROM company_invitations WHERE email = ?",
                email))
        .containsEntry("company_id", company)
        .containsEntry("token_hash", sha256(invitationToken))
        .containsEntry("status", "PENDING");
    assertThat(membershipCount(guest)).isZero();
    assertThat(get(ME + "/users", guestJwt).statusCode()).isEqualTo(404);

    var accepted = accept(invitationToken, guestJwt);
    assertThat(accepted.statusCode()).isEqualTo(200);
    assertThat(accepted.body()).contains(guest.toString(), "VIEWER", "ACTIVE");
    var ownTeam = get(ME + "/users?companyId=" + otherCompany, guestJwt);
    assertThat(ownTeam.statusCode()).isEqualTo(200);
    assertThat(ownTeam.body())
        .contains(owner.toString(), guest.toString())
        .doesNotContain(outsider.toString(), email);
    assertThat(get(ME + "/users", outsiderJwt).body())
        .contains(outsider.toString())
        .doesNotContain(owner.toString(), guest.toString());

    assertThat(get(AUDIT, ownerJwt).body())
        .contains("MEMBER_ACTIVATED", guest.toString())
        .doesNotContain(invitationToken, sha256(invitationToken), guestJwt, ownerJwt, email);
    assertThat(output.getAll()).doesNotContain(invitationToken, guestJwt, ownerJwt);

    var replay = accept(invitationToken, guestJwt);
    assertThat(replay.statusCode()).isEqualTo(409);
    assertThat(replay.body()).doesNotContain(invitationToken);
    assertThat(membershipCount(guest)).isEqualTo(1);
  }

  @Test
  void enforcesCurrentAccountMembershipAndVerifiedRecipient() throws Exception {
    UUID owner = UUID.randomUUID();
    UUID operator = UUID.randomUUID();
    UUID admin = UUID.randomUUID();
    UUID guest = UUID.randomUUID();
    String email = guest + "@example.com";
    String ownerJwt = token(owner);
    String adminJwt = token(admin);
    UUID company = createCompany(owner, ownerJwt, "Secure clinic", "123456793");
    jdbc.update(
        "INSERT INTO company_memberships (id, company_id, user_id, role) VALUES (?, ?, ?, 'OPERATOR')",
        UUID.randomUUID(),
        company,
        operator);
    jdbc.update(
        """
        INSERT INTO company_memberships (id, company_id, user_id, role, status)
        VALUES (?, ?, ?, 'ADMIN', 'SUSPENDED')
        """,
        UUID.randomUUID(),
        company,
        admin);

    assertThat(invite(null, email, "VIEWER", false).statusCode()).isEqualTo(401);
    assertThat(invite("invalid-jwt", email, "VIEWER", false).statusCode()).isEqualTo(401);
    assertThat(get(ME + "/users", null).statusCode()).isEqualTo(401);
    assertThat(get(ME + "/users", "invalid-jwt").statusCode()).isEqualTo(401);
    assertThat(invite(token(operator), email, "VIEWER", false).statusCode()).isEqualTo(403);
    assertThat(invite(adminJwt, email, "VIEWER", false).statusCode()).isEqualTo(403);
    assertThat(get(ME + "/users", adminJwt).statusCode()).isEqualTo(403);
    jdbc.update("UPDATE company_memberships SET status = 'ACTIVE' WHERE user_id = ?", admin);
    ACCOUNT_STATUS.put(admin.toString(), 403);
    assertThat(invite(adminJwt, email, "VIEWER", false).statusCode()).isEqualTo(403);
    ACCOUNT_STATUS.remove(admin.toString());
    assertThat(invite(adminJwt, email, "ADMIN", false).statusCode()).isEqualTo(400);
    assertThat(invite(adminJwt, email, "OPERATOR", false).statusCode()).isEqualTo(201);
    String invitationToken = DELIVERIES.remove(email);

    assertThat(accept(invitationToken, null).statusCode()).isEqualTo(401);
    assertThat(accept(invitationToken, "invalid-jwt").statusCode()).isEqualTo(401);
    assertThat(accept(invitationToken, token(guest)).statusCode()).isEqualTo(403);
    assertThat(accept(invitationToken, verifiedToken(guest, email, false)).statusCode())
        .isEqualTo(403);
    assertThat(
            accept(invitationToken, verifiedToken(guest, "wrong@example.com", true)).statusCode())
        .isEqualTo(403);
    assertThat(accept("invalid", verifiedToken(guest, email, true)).statusCode()).isEqualTo(400);
    assertThat(accept("a".repeat(43), verifiedToken(guest, email, true)).statusCode())
        .isEqualTo(404);
    assertThat(membershipCount(guest)).isZero();
    assertThat(accept(invitationToken, verifiedToken(owner, email, true)).statusCode())
        .isEqualTo(409);
    assertThat(
            accept(
                    invitationToken,
                    verifiedToken(guest, email.toUpperCase(java.util.Locale.ROOT), true))
                .statusCode())
        .isEqualTo(200);
  }

  @Test
  void duplicateReplacementAndExpirationInvalidateOldTokens() throws Exception {
    UUID owner = UUID.randomUUID();
    UUID guest = UUID.randomUUID();
    String ownerJwt = token(owner);
    String email = guest + "@example.com";
    String guestJwt = verifiedToken(guest, email, true);
    createCompany(owner, ownerJwt, "Invitations clinic", "123456794");

    assertThat(invite(ownerJwt, email, "VIEWER", false).statusCode()).isEqualTo(201);
    String original = DELIVERIES.remove(email);
    assertThat(invite(ownerJwt, email, "VIEWER", false).statusCode()).isEqualTo(409);
    assertThat(DELIVERIES).doesNotContainKey(email);
    assertThat(invite(ownerJwt, email, "VIEWER", true).statusCode()).isEqualTo(201);
    String replacement = DELIVERIES.remove(email);
    assertThat(replacement).isNotEqualTo(original);
    assertThat(accept(original, guestJwt).statusCode()).isEqualTo(409);
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM company_invitations WHERE token_hash = ?",
                String.class,
                sha256(original)))
        .isEqualTo("REVOKED");
    jdbc.update(
        """
        UPDATE company_invitations SET created_at = CURRENT_TIMESTAMP - INTERVAL '2 hours',
          expires_at = CURRENT_TIMESTAMP - INTERVAL '1 hour' WHERE token_hash = ?
        """,
        sha256(replacement));
    assertThat(accept(replacement, guestJwt).statusCode()).isEqualTo(409);
    assertThat(membershipCount(guest)).isZero();
    assertThat(invite(ownerJwt, email, "OPERATOR", false).statusCode()).isEqualTo(201);
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM company_invitations WHERE token_hash = ?",
                String.class,
                sha256(replacement)))
        .isEqualTo("EXPIRED");
    assertThat(accept(DELIVERIES.remove(email), guestJwt).statusCode()).isEqualTo(200);
  }

  @Test
  void concurrentAcceptanceConsumesOneTokenAndCreatesOnlyOneMembership() throws Exception {
    UUID owner = UUID.randomUUID();
    UUID guest = UUID.randomUUID();
    String ownerJwt = token(owner);
    String email = guest + "@example.com";
    String guestJwt = verifiedToken(guest, email, true);
    createCompany(owner, ownerJwt, "Concurrent clinic", "123456795");
    assertThat(invite(ownerJwt, email, "OPERATOR", false).statusCode()).isEqualTo(201);
    String invitationToken = DELIVERIES.remove(email);
    var request = acceptRequest(invitationToken, guestJwt);
    var first = http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    var second = http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    assertThat(
            List.of(
                first.get(10, TimeUnit.SECONDS).statusCode(),
                second.get(10, TimeUnit.SECONDS).statusCode()))
        .containsExactlyInAnyOrder(200, 409);
    assertThat(membershipCount(guest)).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM company_invitations WHERE token_hash = ?",
                String.class,
                sha256(invitationToken)))
        .isEqualTo("ACCEPTED");
  }

  @Test
  void existingOwnerCannotAcceptAnInvitationFromAnotherCompany() throws Exception {
    UUID inviter = UUID.randomUUID();
    UUID guest = UUID.randomUUID();
    String email = guest + "@example.com";
    String inviterJwt = token(inviter);
    String guestJwt = verifiedToken(guest, email, true);
    createCompany(inviter, inviterJwt, "Inviter clinic", "123456796");
    UUID guestCompany = createCompany(guest, guestJwt, "Guest clinic", "123456797");
    assertThat(invite(inviterJwt, email, "OPERATOR", false).statusCode()).isEqualTo(201);
    String invitationToken = DELIVERIES.remove(email);
    assertThat(accept(invitationToken, guestJwt).statusCode()).isEqualTo(409);
    assertThat(
            jdbc.queryForObject(
                "SELECT company_id FROM company_memberships WHERE user_id = ?", UUID.class, guest))
        .isEqualTo(guestCompany);
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM company_invitations WHERE token_hash = ?",
                String.class,
                sha256(invitationToken)))
        .isEqualTo("PENDING");
  }

  @Test
  @Tag("smoke")
  void ownerChangesRoleAndRevokesAccessUsingTheSameMemberToken() throws Exception {
    UUID owner = UUID.randomUUID();
    UUID member = UUID.randomUUID();
    String ownerJwt = token(owner);
    String memberJwt = token(member);
    UUID company = createCompany(owner, ownerJwt, "Member smoke clinic", "123456801");
    addMember(company, member, "VIEWER");
    String rolePath = memberPath(member) + "/role";
    var roleChange = patch(rolePath, ownerJwt, roleBody("ADMIN"));
    assertThat(roleChange.statusCode()).isEqualTo(200);
    assertThat(roleChange.body()).contains(member.toString(), "ADMIN", "ACTIVE");
    assertThat(get(ME + "/users", ownerJwt).body()).contains(member.toString(), "ADMIN");
    assertThat(patch(ME, memberJwt, "{\"location\":\"Santiago\"}").statusCode()).isEqualTo(200);

    var roleAudit = get(AUDIT, ownerJwt);
    assertThat(roleAudit.statusCode()).isEqualTo(200);
    assertThat(roleAudit.body())
        .contains(
            "ROLE_CHANGED", "SUCCESS", owner.toString(), member.toString(), company.toString())
        .contains("\"previousRole\":\"VIEWER\"", "\"newRole\":\"ADMIN\"")
        .doesNotContain(ownerJwt, memberJwt);

    assertThat(deleteMember(member, ownerJwt).statusCode()).isEqualTo(204);
    assertThat(get(AUDIT + "?size=1", ownerJwt).body())
        .contains("MEMBER_REVOKED", "SUCCESS", "\"hasNext\":true")
        .doesNotContain("ROLE_CHANGED");
    assertThat(get(AUDIT + "?page=1&size=1", ownerJwt).body())
        .contains("ROLE_CHANGED", "\"hasNext\":false")
        .doesNotContain("MEMBER_REVOKED");
    assertThat(get(ME + "/users", memberJwt).statusCode()).isEqualTo(403);
    assertThat(patch(ME, memberJwt, "{\"location\":\"La Vega\"}").statusCode()).isEqualTo(403);
    assertThat(patch(rolePath, memberJwt, roleBody("ADMIN")).statusCode()).isEqualTo(403);
    assertThat(deleteMember(member, memberJwt).statusCode()).isEqualTo(403);
    assertThat(get(ME + "/users", ownerJwt).body()).contains(member.toString(), "REVOKED");
    assertThat(membershipCount(member)).isEqualTo(1);
    assertThat(deleteMember(member, ownerJwt).statusCode()).isEqualTo(404);
  }

  @Test
  void auditUsesCurrentMembershipAndRejectsManipulatedFiltersAndMutations() throws Exception {
    UUID owner = UUID.randomUUID();
    UUID auditor = UUID.randomUUID();
    UUID outsider = UUID.randomUUID();
    String ownerJwt = token(owner);
    String auditorJwt = token(auditor);
    String outsiderJwt = token(outsider);
    UUID company = createCompany(owner, ownerJwt, "Audit clinic", "123456810");
    UUID otherCompany = createCompany(outsider, outsiderJwt, "Audit outsider", "123456811");
    addMember(company, auditor, "VIEWER");
    assertThat(patch(memberPath(auditor) + "/role", ownerJwt, roleBody("ADMIN")).statusCode())
        .isEqualTo(200);

    assertThat(get(AUDIT, null).statusCode()).isEqualTo(401);
    assertThat(get(AUDIT, "invalid-token").statusCode()).isEqualTo(401);
    assertThat(get(AUDIT, auditorJwt).statusCode()).isEqualTo(403);
    var crossCompany =
        request(AUDIT + "?companyId=" + company + "&actorId=" + owner)
            .header("Authorization", "Bearer " + outsiderJwt)
            .header("X-Company-Id", company.toString())
            .GET()
            .build();
    var otherAudit = http.send(crossCompany, HttpResponse.BodyHandlers.ofString());
    assertThat(otherAudit.statusCode()).isEqualTo(200);
    assertThat(otherAudit.body()).contains("\"content\":[]").doesNotContain(company.toString());

    UUID permission = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO company_permissions (id, company_id, permission) VALUES (?, ?, ?)",
        permission,
        otherCompany,
        "AUDIT_LOG_READ");
    jdbc.update(
        """
        INSERT INTO membership_permissions (membership_id, permission_id)
        SELECT id, ? FROM company_memberships WHERE company_id = ? AND user_id = ?
        """,
        permission,
        company,
        auditor);
    assertThat(get(AUDIT, auditorJwt).statusCode()).isEqualTo(403);
    jdbc.update("DELETE FROM membership_permissions WHERE permission_id = ?", permission);
    jdbc.update("UPDATE company_permissions SET company_id = ? WHERE id = ?", company, permission);
    jdbc.update(
        """
        INSERT INTO membership_permissions (membership_id, permission_id)
        SELECT id, ? FROM company_memberships WHERE company_id = ? AND user_id = ?
        """,
        permission,
        company,
        auditor);
    assertThat(get(AUDIT, auditorJwt).statusCode()).isEqualTo(200);
    ACCOUNT_TYPE.put(auditor.toString(), "ADVISOR");
    assertThat(get(AUDIT, auditorJwt).statusCode()).isEqualTo(403);
    ACCOUNT_TYPE.remove(auditor.toString());
    jdbc.update("DELETE FROM membership_permissions WHERE permission_id = ?", permission);
    assertThat(get(AUDIT, auditorJwt).statusCode()).isEqualTo(403);

    for (String filter :
        List.of(
            "from=bad-date",
            "page=-1",
            "size=101",
            "page=2147483648",
            "from=2026-02-01T00:00:00Z&to=2026-01-01T00:00:00Z")) {
      assertThat(get(AUDIT + "?" + filter, ownerJwt).statusCode()).as(filter).isEqualTo(400);
    }
    assertThat(get(AUDIT + "?to=2000-01-01T00:00:00Z", ownerJwt).body()).contains("\"content\":[]");
    var deleted = request(AUDIT).header("Authorization", "Bearer " + ownerJwt).DELETE().build();
    assertThat(http.send(deleted, HttpResponse.BodyHandlers.ofString()).statusCode())
        .isEqualTo(405);
    assertThat(patch(AUDIT, ownerJwt, "{}").statusCode()).isEqualTo(405);
    assertThat(get(AUDIT, ownerJwt).body()).contains("ROLE_CHANGED");
  }

  @Test
  void rollbackDiscardsSuccessfulAuditAndRejectedAttemptSurvivesBusinessRollback()
      throws Exception {
    UUID owner = UUID.randomUUID();
    UUID member = UUID.randomUUID();
    String ownerJwt = token(owner);
    UUID company = createCompany(owner, ownerJwt, "Audit rollback clinic", "123456812");
    addMember(company, member, "VIEWER");

    new TransactionTemplate(transactionManager)
        .executeWithoutResult(transaction -> {
          changeMemberRole.change(owner, member, CompanyRole.ADMIN);
          assertThat(
                  jdbc.queryForObject(
                      "SELECT count(*) FROM company_audit_log WHERE company_id = ?",
                      Integer.class,
                      company))
              .isEqualTo(1);
          transaction.setRollbackOnly();
        });
    assertThat(get(AUDIT, ownerJwt).body()).contains("\"content\":[]");
    assertThat(memberships.findByCompanyIdAndUserId(company, member).orElseThrow().role())
        .isEqualTo(CompanyRole.VIEWER);

    assertThat(deleteMember(owner, ownerJwt).statusCode()).isEqualTo(409);
    assertThat(get(AUDIT, ownerJwt).body())
        .contains("MEMBER_REVOKED", "REJECTED")
        .doesNotContain("SUCCESS");
    assertThat(get(ME, ownerJwt).statusCode()).isEqualTo(200);
  }

  @Test
  void enforcesMemberAdministrationAuthorizationAndCompanyScope() throws Exception {
    UUID owner = UUID.randomUUID();
    UUID outsider = UUID.randomUUID();
    UUID admin = UUID.randomUUID();
    UUID operator = UUID.randomUUID();
    UUID viewer = UUID.randomUUID();
    String ownerJwt = token(owner);
    UUID company = createCompany(owner, ownerJwt, "Member security clinic", "123456802");
    UUID otherCompany =
        createCompany(outsider, token(outsider), "Outside member clinic", "123456803");
    addMember(company, admin, "ADMIN");
    addMember(company, operator, "OPERATOR");
    addMember(company, viewer, "VIEWER");
    String rolePath = memberPath(operator) + "/role";
    String promote = roleBody("ADMIN");

    assertThat(patch(rolePath, null, promote).statusCode()).isEqualTo(401);
    assertThat(patch(rolePath, "invalid-token", promote).statusCode()).isEqualTo(401);
    assertThat(deleteMember(operator, null).statusCode()).isEqualTo(401);
    assertThat(deleteMember(operator, "invalid-token").statusCode()).isEqualTo(401);
    assertThat(patch(rolePath, token(operator), promote).statusCode()).isEqualTo(403);
    assertThat(patch(rolePath, token(viewer), promote).statusCode()).isEqualTo(403);
    assertThat(deleteMember(operator, token(viewer)).statusCode()).isEqualTo(403);
    assertThat(deleteMember(viewer, token(operator)).statusCode()).isEqualTo(403);
    assertThat(patch(rolePath, token(admin), roleBody("OWNER")).statusCode()).isEqualTo(403);
    assertThat(patch(memberPath(owner) + "/role", token(admin), promote).statusCode())
        .isEqualTo(403);
    assertThat(deleteMember(owner, token(admin)).statusCode()).isEqualTo(403);
    assertThat(deleteMember(owner, ownerJwt).statusCode()).isEqualTo(409);
    assertThat(patch(memberPath(owner) + "/role", ownerJwt, promote).statusCode()).isEqualTo(409);

    String outsiderPath = memberPath(outsider) + "?companyId=" + otherCompany;
    var crossCompany =
        request(outsiderPath)
            .header("Authorization", "Bearer " + ownerJwt)
            .header("X-Company-Id", otherCompany.toString())
            .DELETE()
            .build();
    assertThat(http.send(crossCompany, HttpResponse.BodyHandlers.ofString()).statusCode())
        .isEqualTo(404);
    assertThat(patch(memberPath(outsider) + "/role", ownerJwt, promote).statusCode())
        .isEqualTo(404);
    assertThat(patch(ME + "/members/not-a-uuid/role", ownerJwt, promote).statusCode())
        .isEqualTo(400);
    for (String body :
        List.of(
            "{}",
            "{\"role\":null}",
            "{\"role\":0}",
            roleBody("READ_ONLY"),
            "{\"role\":\"VIEWER\",\"companyId\":\"" + otherCompany + "\"}")) {
      assertThat(patch(rolePath, ownerJwt, body).statusCode()).as(body).isEqualTo(400);
    }
    assertThat(get(ME + "/users", token(outsider)).body()).contains(outsider.toString(), "OWNER");
  }

  @Test
  void concurrentRevocationAndDemotionLeaveExactlyOneActiveOwner() throws Exception {
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    String firstJwt = token(first);
    String secondJwt = token(second);
    UUID company = createCompany(first, firstJwt, "Member concurrency clinic", "123456804");
    addMember(company, second, "OPERATOR");
    assertThat(patch(memberPath(second) + "/role", firstJwt, roleBody("OWNER")).statusCode())
        .isEqualTo(200);

    var demotionRequest = patchRequest(memberPath(first) + "/role", firstJwt, roleBody("ADMIN"));
    var revocationRequest = deleteMemberRequest(second, secondJwt);
    var operations =
        new TransactionTemplate(transactionManager)
            .execute(transaction -> {
              memberships.lockMembershipChanges(company);
              var demotion = http.sendAsync(demotionRequest, HttpResponse.BodyHandlers.ofString());
              var revocation =
                  http.sendAsync(revocationRequest, HttpResponse.BodyHandlers.ofString());
              awaitContendingMembershipChanges();
              return List.of(demotion, revocation);
            });
    int demotionStatus = operations.get(0).get(10, TimeUnit.SECONDS).statusCode();
    int revocationStatus = operations.get(1).get(10, TimeUnit.SECONDS).statusCode();
    assertThat(List.of(demotionStatus, revocationStatus))
        .isIn(List.of(200, 409), List.of(409, 204));
    assertThat(
            jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM company_memberships
                WHERE company_id = ? AND role = 'OWNER' AND status = 'ACTIVE'
                """,
                Integer.class,
                company))
        .isEqualTo(1);
  }

  private void awaitContendingMembershipChanges() {
    long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
    while (System.nanoTime() < deadline) {
      int waiting =
          jdbc.queryForObject(
              """
              SELECT COUNT(*) FROM pg_locks
              WHERE locktype = 'advisory' AND NOT granted
                AND database = (SELECT oid FROM pg_database WHERE datname = current_database())
              """,
              Integer.class);
      if (waiting == 2) {
        return;
      }
      try {
        Thread.sleep(20);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new AssertionError(
            "Interrupted while waiting for both membership changes", interrupted);
      }
    }
    throw new AssertionError("Both membership changes must contend for the company lock");
  }

  private void addMember(UUID company, UUID user, String role) {
    jdbc.update(
        "INSERT INTO company_memberships (id, company_id, user_id, role) VALUES (?, ?, ?, ?)",
        UUID.randomUUID(),
        company,
        user,
        role);
  }

  private static String memberPath(UUID user) {
    return ME + "/members/" + user;
  }

  private static String roleBody(String role) {
    return "{\"role\":\"%s\"}".formatted(role);
  }

  private HttpRequest deleteMemberRequest(UUID user, String token) {
    var request = request(memberPath(user));
    if (token != null) {
      request.header("Authorization", "Bearer " + token);
    }
    return request.DELETE().build();
  }

  private HttpResponse<String> deleteMember(UUID user, String token) throws Exception {
    return http.send(deleteMemberRequest(user, token), HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> invite(String jwt, String email, String role, boolean replace)
      throws Exception {
    var request = request(ME + "/invitations").header("Content-Type", "application/json");
    if (jwt != null) {
      request.header("Authorization", "Bearer " + jwt);
    }
    return http.send(
        request
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    """
                    {"email":"%s","role":"%s","replace":%s}
                    """
                        .formatted(email, role, replace)))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> accept(String invitationToken, String jwt) throws Exception {
    return http.send(acceptRequest(invitationToken, jwt), HttpResponse.BodyHandlers.ofString());
  }

  private HttpRequest acceptRequest(String invitationToken, String jwt) {
    var request = request("/api/v1/invitations/" + invitationToken + "/accept");
    if (jwt != null) {
      request.header("Authorization", "Bearer " + jwt);
    }
    return request.POST(HttpRequest.BodyPublishers.noBody()).build();
  }

  private int membershipCount(UUID userId) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM company_memberships WHERE user_id = ?", Integer.class, userId);
  }

  private String verifiedToken(UUID userId, String email, boolean emailVerified)
      throws JOSEException {
    return token(
        userId.toString(), Instant.now().plusSeconds(300), signingKey, email, emailVerified);
  }

  private static String sha256(String token) throws Exception {
    return HexFormat.of()
        .formatHex(
            MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)));
  }

  private Instant updatedAt(UUID company) {
    return jdbc.queryForObject(
        "SELECT updated_at FROM companies WHERE id = ?",
        (rs, rowNum) -> rs.getObject(1, OffsetDateTime.class).toInstant(),
        company);
  }

  private HttpResponse<String> patch(String path, String token, String body) throws Exception {
    return http.send(patchRequest(path, token, body), HttpResponse.BodyHandlers.ofString());
  }

  private HttpRequest patchRequest(String path, String token, String body) {
    var request = request(path).header("Content-Type", "application/json");
    if (token != null) {
      request.header("Authorization", "Bearer " + token);
    }
    return request.method("PATCH", HttpRequest.BodyPublishers.ofString(body)).build();
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
                    {"companyName":"%s SRL","businessName":"%s","rnc":%s,
                     "email":"company@example.com","phone":"8095555555","location":"Santo Domingo"}
                    """
                        .formatted(name, name, rnc == null ? "null" : "\"%s\"".formatted(rnc))))
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
    return token(userId.toString(), Instant.now().plusSeconds(300), signingKey);
  }

  private String token(String subject, Instant expires, RSAPrivateKey signingKey)
      throws JOSEException {
    return token(subject, expires, signingKey, null, false);
  }

  private String token(
      String subject,
      Instant expires,
      RSAPrivateKey signingKey,
      String email,
      boolean emailVerified)
      throws JOSEException {
    var claims =
        new JWTClaimsSet.Builder()
            .subject(subject)
            .issuer(issuer)
            .audience(audience)
            .issueTime(Date.from(Instant.now().minusSeconds(30)));
    if (expires != null) {
      claims.expirationTime(Date.from(expires));
    }
    if (email != null) {
      claims.claim("email", email).claim("email_verified", emailVerified);
    }
    var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
    jwt.sign(new RSASSASigner(signingKey));
    return jwt.serialize();
  }

  private static RSAPrivateKey signingKey() {
    try (var input = new ClassPathResource("jwt-test-private.pem").getInputStream()) {
      return RsaKeyConverters.pkcs8().convert(input);
    } catch (IOException failure) {
      throw new IllegalStateException(failure);
    }
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
