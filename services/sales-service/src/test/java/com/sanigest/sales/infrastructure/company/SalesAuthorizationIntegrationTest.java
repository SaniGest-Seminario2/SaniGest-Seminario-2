package com.sanigest.sales.infrastructure.company;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.sanigest.companyaccess.CompanyAccessProtocol.PATH;
import static com.sanigest.companyaccess.CompanyAccessProtocol.USER_ASSERTION;
import static com.sanigest.sales.application.port.out.integration.SalesAuthorization.Permission.SALES_CANCEL;
import static com.sanigest.sales.application.port.out.integration.SalesAuthorization.Permission.SALES_READ;
import static com.sanigest.sales.application.port.out.integration.SalesAuthorization.Permission.SALES_WRITE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sanigest.sales.application.exception.SalesAccessForbidden;
import com.sanigest.sales.application.exception.SalesAuthorizationUnavailable;
import com.sanigest.sales.application.port.out.integration.SalesAuthorization;
import com.sanigest.sales.application.port.out.integration.SalesAuthorization.Permission;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@SpringBootTest(
    classes = SalesAuthorizationIntegrationTest.TestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "sales-authorization-test"})
class SalesAuthorizationIntegrationTest {
  private static final UUID USER = UUID.randomUUID();
  private static final UUID COMPANY = UUID.randomUUID();
  private static final UUID FOREIGN_COMPANY = UUID.randomUUID();
  private static final UUID OWN_SALE = UUID.randomUUID();
  private static final UUID FOREIGN_SALE = UUID.randomUUID();
  private static final String ISSUER = "sanigest-test";
  private static final String AUDIENCE = "sanigest-api-test";
  private static final String SERVICE_ISSUER = "sales-company-access";
  private static final KeyPair USER_KEYS = keys();
  private static final KeyPair SERVICE_KEYS = keys();
  @TempDir static Path credentials;

  @Autowired private JwtDecoder decoder;
  @Autowired private SalesAuthorization authorization;
  @Autowired private ProtectedOperation operation;
  @LocalServerPort private int port;
  private static final WireMockServer REMOTE = new WireMockServer(0);

  @DynamicPropertySource
  static void configure(DynamicPropertyRegistry registry) throws Exception {
    Path publicKey = pem("session.pem", "PUBLIC KEY", USER_KEYS.getPublic().getEncoded());
    Path privateKey = pem("sales.pem", "PRIVATE KEY", SERVICE_KEYS.getPrivate().getEncoded());
    REMOTE.start();
    registry.add("sanigest.company-access.origin", REMOTE::baseUrl);
    registry.add("sanigest.jwt.public-key", () -> publicKey.toUri().toString());
    registry.add(
        "sanigest.company-access.private-key-location", () -> privateKey.toUri().toString());
    registry.add("sanigest.company-access.enabled", () -> true);
    registry.add("sanigest.company-access.response-timeout", () -> "600ms");
    registry.add("management.endpoint.health.group.readiness.include", () -> "readinessState");
  }

  @AfterAll
  static void stopCompany() {
    REMOTE.stop();
  }

  @BeforeEach
  void resetCompany() {
    REMOTE.resetAll();
  }

  @AfterEach
  void clearSession() {
    SecurityContextHolder.clearContext();
    RequestContextHolder.resetRequestAttributes();
  }

  @ParameterizedTest
  @CsvSource({
    "OWNER, false, SALES_CANCEL, WRITE, true",
    "ADMIN, false, SALES_CANCEL, WRITE, true",
    "OPERATOR, true, SALES_WRITE, WRITE, true",
    "OPERATOR, false, SALES_WRITE, WRITE, false",
    "OPERATOR, true, SALES_CANCEL, WRITE, false",
    "OPERATOR, false, SALES_READ, READ, true",
    "VIEWER, false, SALES_READ, READ, true",
    "VIEWER, true, SALES_WRITE, WRITE, false"
  })
  void appliesCurrentMembershipThroughSpringAndCompany(
      String role, boolean grant, Permission permission, String scope, boolean allowed)
      throws Exception {
    String bearer = authenticate();
    reply(membership(role, scope, grant));
    if (allowed) {
      assertThat(operation.membership(permission)).isEqualTo(COMPANY);
    } else {
      assertThatThrownBy(() -> operation.membership(permission))
          .isInstanceOf(SalesAccessForbidden.class);
    }
    var request = REMOTE.findAll(getRequestedFor(urlEqualTo(PATH))).getFirst();
    assertThat(request.getHeader(USER_ASSERTION)).isEqualTo(bearer);
    assertThat(request.getHeader("X-Trace-Id")).matches("[a-f0-9]{32}");
    SignedJWT assertion = assertion();
    assertThat(assertion.verify(new RSASSAVerifier((RSAPublicKey) SERVICE_KEYS.getPublic())))
        .isTrue();
    assertThat(assertion.getJWTClaimsSet().getIssuer()).isEqualTo(SERVICE_ISSUER);
    assertThat(assertion.getJWTClaimsSet().getSubject()).isEqualTo(USER.toString());
    assertThat(assertion.getJWTClaimsSet().getStringClaim("module")).isEqualTo("SALES");
    assertThat(assertion.getJWTClaimsSet().getStringClaim("scope")).isEqualTo(scope);
    assertThat(assertion.getJWTClaimsSet().getClaim("company_id")).isEqualTo(COMPANY.toString());
  }

  @Test
  void usesOnlyResolvedCompanyForAccessToAnObject() throws Exception {
    authenticate();
    var request = new MockHttpServletRequest();
    request.addHeader("X-Company-Id", COMPANY.toString());
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    reply(membership("OWNER", "READ", false));
    assertThat(operation.membership(SALES_READ)).isEqualTo(COMPANY);
    assertThat(operation.findSale(OWN_SALE)).contains(OWN_SALE);
    assertThat(operation.findSale(FOREIGN_SALE)).isEmpty();
    for (var companyRequest : REMOTE.findAll(getRequestedFor(urlEqualTo(PATH)))) {
      assertThat(companyRequest.containsHeader("X-Company-Id")).isFalse();
    }
  }

  @Test
  void requiresCurrentDelegationForTheExactTenantAndScope() throws Exception {
    authenticate();
    reply(aResponse().withStatus(403));
    assertThatThrownBy(() -> operation.membership(SALES_READ))
        .isInstanceOf(SalesAccessForbidden.class);
    assertThatThrownBy(() -> operation.delegated(COMPANY, SALES_READ))
        .isInstanceOf(SalesAccessForbidden.class);

    reply(delegation(COMPANY, "READ"));
    assertThat(operation.delegated(COMPANY, SALES_READ)).isEqualTo(COMPANY);
    assertThat(assertion().getJWTClaimsSet().getStringClaim("company_id"))
        .isEqualTo(COMPANY.toString());
    assertThat(
            REMOTE
                .findAll(getRequestedFor(urlEqualTo(PATH)))
                .getFirst()
                .containsHeader(USER_ASSERTION))
        .isFalse();
    assertThatThrownBy(() -> operation.delegated(FOREIGN_COMPANY, SALES_READ))
        .isInstanceOf(SalesAuthorizationUnavailable.class);
    assertThatThrownBy(() -> operation.delegated(COMPANY, SALES_WRITE))
        .isInstanceOf(SalesAuthorizationUnavailable.class);

    reply(aResponse().withStatus(403));
    assertThatThrownBy(() -> operation.delegated(COMPANY, SALES_WRITE))
        .isInstanceOf(SalesAccessForbidden.class);
    assertThat(assertion().getJWTClaimsSet().getStringClaim("scope")).isEqualTo("WRITE");

    reply(delegation(COMPANY, "WRITE"));
    assertThat(operation.delegated(COMPANY, SALES_WRITE)).isEqualTo(COMPANY);
    reply(delegation(COMPANY, "WRITE"));
    assertThatThrownBy(() -> operation.delegated(COMPANY, SALES_CANCEL))
        .isInstanceOf(SalesAccessForbidden.class);
    REMOTE.verify(0, getRequestedFor(urlEqualTo(PATH)));

    reply(aResponse().withStatus(403));
    assertThatThrownBy(() -> operation.delegated(COMPANY, SALES_WRITE))
        .isInstanceOf(SalesAccessForbidden.class);
  }

  @Test
  void rechecksRevokedMembershipGrantOnTheNextOperation() throws Exception {
    authenticate();
    reply(membership("OPERATOR", "WRITE", true));
    assertThat(operation.membership(SALES_WRITE)).isEqualTo(COMPANY);
    reply(membership("OPERATOR", "WRITE", false));
    assertThatThrownBy(() -> operation.membership(SALES_WRITE))
        .isInstanceOf(SalesAccessForbidden.class);
  }

  @Test
  void rejectsAnInvocationOutsideTheAuthenticatedContext() {
    assertThat(AopUtils.isAopProxy(operation)).isTrue();
    assertThatThrownBy(() -> operation.membership(SALES_READ))
        .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    assertThatThrownBy(() -> authorization.requireMembership(SALES_READ))
        .isInstanceOf(SalesAccessForbidden.class);
    REMOTE.verify(0, getRequestedFor(urlEqualTo(PATH)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"json", "user", "role", "scope", "module", "mode", "503", "timeout"})
  void failsClosedWhenCompanyCannotReturnAValidDecision(String failure) throws Exception {
    authenticate();
    reply(
        switch (failure) {
          case "json" -> json("{");
          case "user" ->
              json(
                  """
                  {"userId":"%s","companyId":"%s","module":"SALES","scope":"WRITE",
                   "mode":"MEMBERSHIP","role":"OWNER","explicitGrant":true}
                  """
                      .formatted(UUID.randomUUID(), COMPANY));
          case "role" -> membership("UNKNOWN", "WRITE", true);
          case "scope" -> membership("OWNER", "CANCEL", true);
          case "module" -> membership("OWNER", "WRITE", true, "CATALOG");
          case "mode" -> delegation(COMPANY, "WRITE");
          case "503" -> aResponse().withStatus(503);
          case "timeout" -> membership("OWNER", "WRITE", true).withFixedDelay(1500);
          default -> throw new AssertionError(failure);
        });
    assertThatThrownBy(() -> operation.membership(SALES_WRITE))
        .isInstanceOf(SalesAuthorizationUnavailable.class);
  }

  @Test
  void keepsHealthPublicAndUnimplementedSalesRoutesDenied() throws Exception {
    assertThat(status("/actuator/health", "GET", null)).isEqualTo(200);
    String valid = token(USER_KEYS, ISSUER, AUDIENCE, Instant.now().plusSeconds(60));
    assertThat(status("/actuator/health", "GET", valid)).isEqualTo(200);
    assertThat(status("/api/v1/sales", "GET", null)).isEqualTo(401);
    assertThat(status("/api/v1/sales", "POST", null)).isEqualTo(401);
    assertThat(status("/api/v1/sales/" + UUID.randomUUID(), "GET", null)).isEqualTo(401);
    assertDeniedRoute("DELETE", "/api/v1/sales/" + UUID.randomUUID(), valid);
    assertThat(status("/api/v1/sales/" + UUID.randomUUID() + "/cancel", "POST", null))
        .isEqualTo(401);
    assertDeniedRoute("GET", "/api/v1/sales/" + UUID.randomUUID() + "/cancel", valid);
    REMOTE.verify(0, getRequestedFor(urlEqualTo(PATH)));
  }

  @Test
  void rejectsInvalidSignatureIssuerAudienceAndExpirationAtTheFilter() throws Exception {
    Instant future = Instant.now().plusSeconds(60);
    for (String invalid :
        new String[] {
          "invalid",
          token(SERVICE_KEYS, ISSUER, AUDIENCE, future),
          token(USER_KEYS, "other-issuer", AUDIENCE, future),
          token(USER_KEYS, ISSUER, "other-audience", future),
          token(USER_KEYS, ISSUER, AUDIENCE, Instant.now().minusSeconds(5))
        }) {
      assertThat(status("/actuator/health", "GET", invalid)).isEqualTo(401);
    }
  }

  private void assertDeniedRoute(String method, String path, String bearer) throws Exception {
    assertThat(status(path, method, null)).isEqualTo(401);
    assertThat(status(path, method, bearer)).isEqualTo(403);
  }

  private int status(String path, String method, String bearer) throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("X-Company-Id", FOREIGN_COMPANY.toString())
            .method(method, HttpRequest.BodyPublishers.noBody());
    if (bearer != null) {
      request.header("Authorization", "Bearer " + bearer);
    }
    try (var http = HttpClient.newHttpClient()) {
      return http.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }
  }

  private String authenticate() throws Exception {
    var request = new MockHttpServletRequest();
    request.addHeader("X-Company-Id", COMPANY.toString());
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    String bearer = token(USER_KEYS, ISSUER, AUDIENCE, Instant.now().plusSeconds(60));
    SecurityContextHolder.getContext()
        .setAuthentication(new JwtAuthenticationConverter().convert(decoder.decode(bearer)));
    return bearer;
  }

  private void reply(ResponseDefinitionBuilder answer) {
    REMOTE.resetAll();
    REMOTE.stubFor(get(urlEqualTo(PATH)).willReturn(answer));
  }

  private SignedJWT assertion() throws Exception {
    String bearer =
        REMOTE.findAll(getRequestedFor(urlEqualTo(PATH))).getFirst().getHeader("Authorization");
    return SignedJWT.parse(bearer.substring("Bearer ".length()));
  }

  private static ResponseDefinitionBuilder membership(String role, String scope, boolean grant) {
    return membership(role, scope, grant, "SALES");
  }

  private static ResponseDefinitionBuilder membership(
      String role, String scope, boolean grant, String module) {
    return json(
        """
        {"userId":"%s","companyId":"%s","module":"%s","scope":"%s",
         "mode":"MEMBERSHIP","role":"%s","explicitGrant":%s%s}
        """
            .formatted(
                USER,
                COMPANY,
                module,
                scope,
                role,
                grant,
                "WRITE".equals(scope) && "SALES".equals(module)
                    ? ",\"companyName\":\"Empresa\""
                    : ""));
  }

  private static ResponseDefinitionBuilder delegation(UUID target, String scope) {
    return json(
        """
        {"userId":"%s","companyId":"%s","module":"SALES","scope":"%s","mode":"DELEGATED"}
        """
            .formatted(USER, target, scope));
  }

  private static ResponseDefinitionBuilder json(String body) {
    return aResponse().withHeader("Content-Type", "application/json").withBody(body);
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

  private static Path pem(String file, String label, byte[] encoded) throws java.io.IOException {
    return Files.writeString(
        credentials.resolve(file),
        "-----BEGIN "
            + label
            + "-----\n"
            + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded)
            + "\n-----END "
            + label
            + "-----\n");
  }

  private static String token(KeyPair keys, String issuer, String audience, Instant expires)
      throws Exception {
    var claims =
        new JWTClaimsSet.Builder()
            .issuer(issuer)
            .subject(USER.toString())
            .audience(audience)
            .issueTime(Date.from(Instant.now().minusSeconds(60)))
            .expirationTime(Date.from(expires))
            .claim("company_id", FOREIGN_COMPANY.toString())
            .claim("role", "OWNER")
            .build();
    var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
    jwt.sign(new RSASSASigner(keys.getPrivate()));
    return jwt.serialize();
  }

  @Configuration(proxyBeanMethods = false)
  @Profile("sales-authorization-test")
  @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
  @ComponentScan("com.sanigest.sales.infrastructure.security")
  @Import({CompanyAccessConfiguration.class, CompanySalesAuthorization.class})
  static class TestApplication {
    @Bean
    ProtectedOperation protectedOperation(SalesAuthorization authorization) {
      return new ProtectedOperation(authorization);
    }
  }

  static class ProtectedOperation {
    private final SalesAuthorization authorization;

    ProtectedOperation(SalesAuthorization authorization) {
      this.authorization = authorization;
    }

    @PreAuthorize("isAuthenticated()")
    public UUID membership(Permission permission) {
      return authorization.requireMembership(permission);
    }

    @PreAuthorize("isAuthenticated()")
    public UUID delegated(UUID company, Permission permission) {
      return authorization.requireDelegation(company, permission);
    }

    @PreAuthorize("isAuthenticated()")
    public Optional<UUID> findSale(UUID id) {
      UUID tenant = authorization.requireMembership(SALES_READ);
      return Optional.of(id).filter(sale -> tenant.equals(COMPANY) && sale.equals(OWN_SALE));
    }
  }
}
