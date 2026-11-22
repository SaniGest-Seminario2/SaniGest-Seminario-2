package com.sanigest.companyaccess;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import com.sanigest.companyaccess.CompanyAccess.Module;
import com.sanigest.companyaccess.CompanyAccess.Scope;
import com.sanigest.companyaccess.HttpCompanyAccess.Settings;
import io.micrometer.observation.ObservationRegistry;
import java.net.URI;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(OutputCaptureExtension.class)
class HttpCompanyAccessTest {
  private static final String PATH = CompanyAccessProtocol.PATH;
  private static final UUID USER = UUID.randomUUID();
  private static final UUID COMPANY = UUID.randomUUID();
  private static final String TRACE = "a".repeat(32);
  private static final String SESSION = "sensitive-user-session";
  private static final String ISSUER = "catalog-test-issuer";
  private static final KeyPair KEYS = keys();

  @RegisterExtension
  static final WireMockExtension COMPANY_SERVER = WireMockExtension.newInstance().build();

  @BeforeEach
  void authenticate() {
    authenticate(USER.toString(), Instant.now().plusSeconds(300));
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void resolvesMembershipAndSendsOnlyTheSignedTechnicalContext() throws Exception {
    respond(200, membership());
    assertThat(client().membership(Scope.WRITE))
        .contains(
            new CompanyAccess.MembershipContext(
                COMPANY, CompanyAccess.MembershipRole.OPERATOR, true));

    COMPANY_SERVER.verify(
        getRequestedFor(urlEqualTo(PATH))
            .withHeader("X-Trace-Id", equalTo(TRACE))
            .withHeader(
                "traceparent",
                com.github.tomakehurst.wiremock.client.WireMock.matching(
                    "00-" + TRACE + "-[0-9a-f]{16}-00"))
            .withoutHeader("X-User-Id")
            .withoutHeader("X-Company-Id")
            .withHeader(CompanyAccessProtocol.USER_ASSERTION, equalTo(SESSION)));
    var request = COMPANY_SERVER.getAllServeEvents().getFirst().getRequest();
    String raw = request.getHeader("Authorization");
    assertThat(raw).startsWith("Bearer ").doesNotContain(SESSION);
    var assertion = SignedJWT.parse(raw.substring(7));
    var claims = assertion.getJWTClaimsSet();
    assertThat(assertion.verify(new RSASSAVerifier((RSAPublicKey) KEYS.getPublic()))).isTrue();
    assertThat(assertion.getHeader().getType().toString())
        .isEqualTo(CompanyAccessProtocol.TOKEN_TYPE);
    assertThat(claims.getIssuer()).isEqualTo(ISSUER);
    assertThat(claims.getAudience()).containsExactly(CompanyAccessProtocol.AUDIENCE);
    assertThat(claims.getSubject()).isEqualTo(USER.toString());
    assertThat(claims.getClaim("module")).isEqualTo("CATALOG");
    assertThat(claims.getClaim("scope")).isEqualTo("WRITE");
    assertThat(claims.getClaim("company_id")).isNull();
    assertThat(
            Duration.between(
                claims.getIssueTime().toInstant(), claims.getExpirationTime().toInstant()))
        .isEqualTo(Duration.ofMinutes(1));
  }

  @Test
  void checksDelegationFreshOnEveryCallAndDeniesRevocation() {
    respond(200, delegated(COMPANY));
    var client = client();
    assertThat(client.delegated(COMPANY, Scope.WRITE)).isTrue();
    COMPANY_SERVER.resetAll();
    respond(403, "");
    assertThat(client.delegated(COMPANY, Scope.WRITE)).isFalse();
    assertThat(client.membership(Scope.WRITE)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(ints = {201, 302, 401, 404, 429, 500, 503})
  void treatsUnexpectedStatusAsUnavailable(int status) {
    respond(status, membership());
    assertThatThrownBy(() -> client().membership(Scope.WRITE))
        .isInstanceOf(CompanyAccessUnavailable.class);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "{",
        "{}",
        "null",
        "[]",
        "unknown-module",
        "wrong-scope",
        "wrong-user",
        "invalid-uuid",
        "wrong-role",
        "wrong-mode",
        "missing-boolean",
        "coerced-boolean",
        "duplicate",
        "trailing"
      })
  void rejectsInvalidOrMismatchedSuccess(String invalid) {
    var body =
        switch (invalid) {
          case "unknown-module" -> membership().replace("CATALOG", "UNKNOWN");
          case "wrong-scope" -> membership().replace("WRITE", "READ");
          case "wrong-user" -> membership().replace(USER.toString(), UUID.randomUUID().toString());
          case "invalid-uuid" -> membership().replace(COMPANY.toString(), "1-1-1-1-1");
          case "wrong-role" -> membership().replace("OPERATOR", "UNKNOWN");
          case "wrong-mode" -> membership().replace("MEMBERSHIP", "DELEGATED");
          case "missing-boolean" -> membership().replace(",\"explicitGrant\":true", "");
          case "coerced-boolean" -> membership().replace("true", "\"true\"");
          case "duplicate" -> membership().replace("\"role\":", "\"role\":\"OWNER\",\"role\":");
          case "trailing" -> membership() + "{}";
          default -> invalid;
        };
    respond(200, body);
    assertThatThrownBy(() -> client().membership(Scope.WRITE))
        .isInstanceOf(CompanyAccessUnavailable.class);
  }

  @Test
  void rejectsOtherCompanyInDelegatedResponse() {
    respond(200, delegated(UUID.randomUUID()));
    assertThatThrownBy(() -> client().delegated(COMPANY, Scope.WRITE))
        .isInstanceOf(CompanyAccessUnavailable.class);
  }

  @Test
  void boundsTheResponseTimeWithoutLoggingCredentials(CapturedOutput output) throws Exception {
    var client = client();
    respond(403, "");
    assertThat(client.membership(Scope.WRITE)).isEmpty();
    COMPANY_SERVER.resetAll();
    COMPANY_SERVER.stubFor(
        get(urlEqualTo(PATH))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withFixedDelay(2500)
                    .withBody(membership())));
    assertThatThrownBy(() -> client.membership(Scope.WRITE))
        .isInstanceOf(CompanyAccessUnavailable.class)
        .hasNoCause();
    var request = COMPANY_SERVER.getAllServeEvents().getFirst().getRequest();
    assertThat(output.getAll())
        .contains("Company authorization query failed")
        .doesNotContain(SESSION, request.getHeader("Authorization").substring(7));
  }

  @Test
  void ignoresUnauthenticatedExpiredAndMalformedIdentity() {
    SecurityContextHolder.clearContext();
    var client = client();
    assertThat(client.membership(Scope.WRITE)).isEmpty();
    authenticate("1-1-1-1-1", Instant.now().plusSeconds(300));
    assertThat(client.membership(Scope.WRITE)).isEmpty();
    authenticate(USER.toString(), Instant.now().minusSeconds(1));
    assertThat(client.membership(Scope.WRITE)).isEmpty();
    COMPANY_SERVER.verify(0, getRequestedFor(urlEqualTo(PATH)));
  }

  @Test
  void normalizesTraceAndNeverUsesItAsIdentity() {
    assertThat(TraceIds.normalize(TRACE)).isEqualTo(TRACE);
    for (String input : new String[] {null, "0".repeat(32), "fake\r\nAuthorization: value"}) {
      assertThat(TraceIds.normalize(input)).matches("[0-9a-f]{32}").isNotEqualTo(input);
    }
  }

  @Test
  void requiresValidOriginAndTimeouts() {
    var origin = URI.create(COMPANY_SERVER.baseUrl());
    assertThatThrownBy(() ->
            client(
                new Settings(
                    origin, false, Module.CATALOG, Duration.ofMillis(500), Duration.ofSeconds(2))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() ->
            client(
                new Settings(origin, true, Module.CATALOG, Duration.ofMillis(500), Duration.ZERO)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() ->
            client(
                new Settings(origin, true, Module.CATALOG, Duration.ZERO, Duration.ofSeconds(2))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNonJsonAndOversizedResponses() {
    COMPANY_SERVER.stubFor(
        get(urlEqualTo(PATH)).willReturn(aResponse().withStatus(200).withBody(membership())));
    var client = client();
    assertThatThrownBy(() -> client.membership(Scope.WRITE))
        .isInstanceOf(CompanyAccessUnavailable.class);
    COMPANY_SERVER.resetAll();
    respond(200, membership() + " ".repeat(4096));
    assertThatThrownBy(() -> client.membership(Scope.WRITE))
        .isInstanceOf(CompanyAccessUnavailable.class);
  }

  private static void authenticate(String subject, Instant expiresAt) {
    var jwt =
        Jwt.withTokenValue(SESSION)
            .header("alg", "RS256")
            .subject(subject)
            .claim("userId", UUID.randomUUID().toString())
            .claim("role", "ADVISOR")
            .issuedAt(Instant.now().minusSeconds(30))
            .expiresAt(expiresAt)
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(new JwtAuthenticationToken(jwt, java.util.List.of()));
  }

  private static HttpCompanyAccess client() {
    return client(
        new Settings(
            URI.create(COMPANY_SERVER.baseUrl()),
            true,
            Module.CATALOG,
            Duration.ofMillis(500),
            Duration.ofMillis(750)));
  }

  private static HttpCompanyAccess client(Settings settings) {
    return new HttpCompanyAccess(
        settings,
        ISSUER,
        (RSAPrivateKey) KEYS.getPrivate(),
        () -> TRACE,
        new ObjectMapper(),
        ObservationRegistry.NOOP);
  }

  private static void respond(int status, String body) {
    COMPANY_SERVER.stubFor(
        get(urlEqualTo(PATH))
            .willReturn(
                aResponse()
                    .withStatus(status)
                    .withHeader("Content-Type", "application/json")
                    .withBody(body)));
  }

  private static String membership() {
    return """
    {"userId":"%s","companyId":"%s","module":"CATALOG","scope":"WRITE",
        "mode":"MEMBERSHIP","role":"OPERATOR","explicitGrant":true}
    """
        .formatted(USER, COMPANY);
  }

  private static String delegated(UUID companyId) {
    return """
    {"userId":"%s","companyId":"%s","module":"CATALOG","scope":"WRITE","mode":"DELEGATED"}
    """
        .formatted(USER, companyId);
  }

  private static KeyPair keys() {
    try {
      var generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (java.security.GeneralSecurityException failure) {
      throw new IllegalStateException(failure);
    }
  }
}
