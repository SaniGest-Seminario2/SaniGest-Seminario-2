package com.sanigest.identity.infrastructure.security;

import com.sanigest.security.AccessTokens;
import java.io.IOException;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
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

@Configuration(proxyBeanMethods = false)
class AdvisorEligibilitySecurity {
  private static final String PATH = "/internal/v1/accounts/{userId}/advisor-eligibility";
  private static final String SUBJECT = "company-service";
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
    JwtDecoder decoder = userDecoder;
    if (!issuer.isBlank() || !keyLocation.isBlank()) {
      decoder =
          withServiceCredentials(
              userDecoder, userProperties, userKey, resources, issuer, keyLocation);
    }

    JwtDecoder endpointDecoder = decoder;
    var paths = PathPatternRequestMatcher.withDefaults();
    return http.securityMatcher(paths.matcher(PATH))
        .csrf(csrf -> csrf.disable())
        .sessionManagement(
            sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .headers(headers ->
            headers
                .cacheControl(cache -> cache.disable())
                .addHeaderWriter(new StaticHeadersWriter("Cache-Control", "no-store")))
        .authorizeHttpRequests(requests ->
            requests
                .requestMatchers(paths.matcher(HttpMethod.GET, PATH))
                .access((authentication, request) ->
                    new AuthorizationDecision(
                        authentication.get() instanceof JwtAuthenticationToken token
                            && !issuer.isBlank()
                            && canRead(token.getToken(), issuer)))
                .anyRequest()
                .denyAll())
        .oauth2ResourceServer(resource ->
            resource.jwt(jwt ->
                jwt.decoder(endpointDecoder)
                    .jwtAuthenticationConverter(
                        token -> new JwtAuthenticationToken(token, List.of()))))
        .build();
  }

  private static JwtDecoder withServiceCredentials(
      JwtDecoder userDecoder,
      JwtProperties userProperties,
      RSAPublicKey userKey,
      ResourceLoader resources,
      String issuer,
      String keyLocation)
      throws IOException {
    if (issuer.isBlank()
        || keyLocation.isBlank()
        || issuer.equals(userProperties.issuer())
        || AUDIENCE.equals(userProperties.audience())) {
      throw new IllegalArgumentException("Advisor eligibility requires separate JWT configuration");
    }
    RSAPublicKey serviceKey = AccessTokens.verificationKey(resources.getResource(keyLocation));
    if (serviceKey.getModulus().equals(userKey.getModulus())) {
      throw new IllegalArgumentException("Advisor eligibility requires a dedicated RSA key");
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
            AdvisorEligibilitySecurity::validateServiceClaims));

    // User authentication is recognized here solely to return 403 for valid session JWTs.
    return value -> {
      try {
        return userDecoder.decode(value);
      } catch (JwtException invalidUserToken) {
        return serviceDecoder.decode(value);
      }
    };
  }

  private static OAuth2TokenValidatorResult validateServiceClaims(Jwt jwt) {
    Instant issuedAt = jwt.getIssuedAt();
    Instant expiresAt = jwt.getExpiresAt();
    if (TOKEN_TYPE.equals(jwt.getHeaders().get("typ"))
        && List.of(AUDIENCE).equals(jwt.getAudience())
        && issuedAt != null
        && !issuedAt.isAfter(Instant.now())
        && expiresAt != null
        && expiresAt.isAfter(issuedAt)
        && Duration.between(issuedAt, expiresAt).compareTo(MAX_TTL) <= 0) {
      return OAuth2TokenValidatorResult.success();
    }
    return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
  }

  private static boolean canRead(Jwt jwt, String issuer) {
    return issuer.equals(jwt.getClaimAsString("iss"))
        && SUBJECT.equals(jwt.getSubject())
        && jwt.getClaims().get("scope") instanceof String scopes
        && Arrays.asList(scopes.split(" ")).contains(SCOPE);
  }
}
