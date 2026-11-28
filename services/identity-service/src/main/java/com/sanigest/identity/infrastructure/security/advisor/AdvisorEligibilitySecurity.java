package com.sanigest.identity.infrastructure.security.advisor;

import com.sanigest.identity.infrastructure.security.token.JwtProperties;
import com.sanigest.security.api.error.ServletSecurityProblems;
import com.sanigest.security.token.AccessTokens;
import java.io.IOException;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AdvisorEligibilitySecurity.MemberDirectoryCredentials.class)
class AdvisorEligibilitySecurity {
  private final ServletSecurityProblems problems;

  AdvisorEligibilitySecurity(JsonMapper json) {
    this.problems = new ServletSecurityProblems(json, "FORBIDDEN");
  }

  private static final String PATH = "/internal/v1/accounts/{userId}/advisor-eligibility";
  private static final String SUBJECT = "company-service";
  private static final String DIRECTORY_PATH = "/internal/v1/member-directory";
  private static final String DIRECTORY_AUDIENCE = "sanigest-identity-member-directory";
  private static final String DIRECTORY_SCOPE = "identity:member-directory:read";
  private static final String AUDIENCE = "sanigest-identity-advisor-eligibility";
  private static final String SCOPE = "identity:advisor-eligibility:read";
  private static final String TOKEN_TYPE = "sanigest-service+jwt";
  private static final Duration MAX_TTL = Duration.ofMinutes(2);

  @Bean
  @Order(1)
  SecurityFilterChain advisorEligibilityFilterChain(
      HttpSecurity http,
      JwtDecoder userDecoder,
      JwtProperties userProperties,
      RSAPublicKey userKey,
      ResourceLoader resources,
      @Value("${sanigest.advisor-eligibility.issuer:}") String issuer,
      @Value("${sanigest.advisor-eligibility.public-key-location:}") String keyLocation)
      throws Exception {
    return internalChain(
        http,
        userDecoder,
        userProperties,
        userKey,
        resources,
        new Credentials(issuer, keyLocation),
        new Endpoint(PATH, HttpMethod.GET, AUDIENCE, SCOPE));
  }

  @Bean
  @Order(2)
  SecurityFilterChain memberDirectoryFilterChain(
      HttpSecurity http,
      JwtDecoder userDecoder,
      JwtProperties userProperties,
      RSAPublicKey userKey,
      ResourceLoader resources,
      MemberDirectoryCredentials credentials,
      @Value("${sanigest.advisor-eligibility.public-key-location:}") String advisorKey)
      throws Exception {
    String keyLocation = credentials.publicKeyLocation();
    if (!keyLocation.isBlank()
        && !advisorKey.isBlank()
        && AccessTokens.verificationKey(resources.getResource(keyLocation))
            .getModulus()
            .equals(AccessTokens.verificationKey(resources.getResource(advisorKey)).getModulus())) {
      throw new IllegalArgumentException("Member directory requires a dedicated RSA key");
    }
    return internalChain(
        http,
        userDecoder,
        userProperties,
        userKey,
        resources,
        new Credentials(credentials.issuer(), keyLocation),
        new Endpoint(DIRECTORY_PATH, HttpMethod.POST, DIRECTORY_AUDIENCE, DIRECTORY_SCOPE));
  }

  private SecurityFilterChain internalChain(
      HttpSecurity http,
      JwtDecoder userDecoder,
      JwtProperties userProperties,
      RSAPublicKey userKey,
      ResourceLoader resources,
      Credentials credentials,
      Endpoint endpoint)
      throws Exception {
    String issuer = credentials.issuer();
    String keyLocation = credentials.keyLocation();
    JwtDecoder decoder = userDecoder;
    if (!issuer.isBlank() || !keyLocation.isBlank()) {
      decoder =
          withServiceCredentials(
              userDecoder,
              userProperties,
              userKey,
              resources,
              issuer,
              keyLocation,
              endpoint.audience());
    }

    JwtDecoder endpointDecoder = decoder;
    var paths = PathPatternRequestMatcher.withDefaults();
    problems.configure(http);
    return http.securityMatcher(paths.matcher(endpoint.path()))
        .csrf(csrf -> csrf.disable())
        .sessionManagement(
            sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .headers(headers ->
            headers
                .cacheControl(cache -> cache.disable())
                .addHeaderWriter(new StaticHeadersWriter("Cache-Control", "no-store")))
        .authorizeHttpRequests(requests ->
            requests
                .requestMatchers(paths.matcher(endpoint.method(), endpoint.path()))
                .access((authentication, request) ->
                    new AuthorizationDecision(
                        authentication.get() instanceof JwtAuthenticationToken token
                            && !issuer.isBlank()
                            && canRead(token.getToken(), issuer, endpoint.scope())))
                .anyRequest()
                .denyAll())
        .oauth2ResourceServer(resource -> {
          problems.configure(resource);
          resource.jwt(jwt ->
              jwt.decoder(endpointDecoder)
                  .jwtAuthenticationConverter(
                      token -> new JwtAuthenticationToken(token, List.of())));
        })
        .build();
  }

  private static JwtDecoder withServiceCredentials(
      JwtDecoder userDecoder,
      JwtProperties userProperties,
      RSAPublicKey userKey,
      ResourceLoader resources,
      String issuer,
      String keyLocation,
      String audience)
      throws IOException {
    if (issuer.isBlank()
        || keyLocation.isBlank()
        || issuer.equals(userProperties.issuer())
        || audience.equals(userProperties.audience())) {
      throw new IllegalArgumentException(
          "Internal Identity endpoint requires separate JWT configuration");
    }
    RSAPublicKey serviceKey = AccessTokens.verificationKey(resources.getResource(keyLocation));
    if (serviceKey.getModulus().equals(userKey.getModulus())) {
      throw new IllegalArgumentException("Internal Identity endpoint requires a dedicated RSA key");
    }
    var serviceDecoder =
        NimbusJwtDecoder.withPublicKey(serviceKey)
            .signatureAlgorithm(SignatureAlgorithm.RS256)
            .validateType(false)
            .build();
    serviceDecoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            new JwtIssuerValidator(issuer),
            new JwtTimestampValidator(Duration.ZERO),
            jwt -> validateServiceClaims(jwt, audience)));

    // User authentication is recognized here solely to return 403 for valid session JWTs.
    return value -> {
      try {
        return userDecoder.decode(value);
      } catch (JwtException invalidUserToken) {
        return serviceDecoder.decode(value);
      }
    };
  }

  private static OAuth2TokenValidatorResult validateServiceClaims(Jwt jwt, String audience) {
    Instant issuedAt = jwt.getIssuedAt();
    Instant expiresAt = jwt.getExpiresAt();
    if (TOKEN_TYPE.equals(jwt.getHeaders().get("typ"))
        && List.of(audience).equals(jwt.getAudience())
        && issuedAt != null
        && !issuedAt.isAfter(Instant.now())
        && expiresAt != null
        && expiresAt.isAfter(issuedAt)
        && Duration.between(issuedAt, expiresAt).compareTo(MAX_TTL) <= 0) {
      return OAuth2TokenValidatorResult.success();
    }
    return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
  }

  private static boolean canRead(Jwt jwt, String issuer, String scope) {
    return issuer.equals(jwt.getClaimAsString("iss"))
        && SUBJECT.equals(jwt.getSubject())
        && jwt.getClaims().get("scope") instanceof String scopes
        && Arrays.asList(scopes.split(" ")).contains(scope);
  }

  @ConfigurationProperties("sanigest.member-directory")
  record MemberDirectoryCredentials(String issuer, String publicKeyLocation) {}

  private record Credentials(String issuer, String keyLocation) {}

  private record Endpoint(String path, HttpMethod method, String audience, String scope) {}
}
