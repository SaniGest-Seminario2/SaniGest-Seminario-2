package com.sanigest.company.infrastructure.security;

import com.sanigest.companyaccess.application.port.out.CompanyAccess.Module;
import com.sanigest.companyaccess.infrastructure.integration.protocol.CompanyAccessProtocol;
import com.sanigest.companyaccess.infrastructure.integration.protocol.CompanyAccessProtocol.Query;
import com.sanigest.security.api.error.ServletSecurityProblems;
import com.sanigest.security.token.AccessTokens;
import java.io.IOException;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CompanyAccessSecurity.Trust.class)
class CompanyAccessSecurity {

  @Bean
  @Order(1)
  SecurityFilterChain companyAccessChain(
      HttpSecurity http, JsonMapper json, Trust trust, SecurityConfiguration.JwtProperties users)
      throws Exception {
    var decoders = serviceDecoders(trust, users);
    var paths = PathPatternRequestMatcher.withDefaults();
    var endpoint = CompanyAccessProtocol.PATH;
    var problems = new ServletSecurityProblems(json, "FORBIDDEN");
    problems.configure(http);
    return http.securityMatcher(paths.matcher(endpoint))
        .csrf(csrf -> csrf.disable())
        .sessionManagement(
            sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(requests ->
            requests
                .requestMatchers(paths.matcher(HttpMethod.GET, endpoint))
                .authenticated()
                .anyRequest()
                .denyAll())
        .oauth2ResourceServer(resource -> {
          problems.configure(resource);
          resource.jwt(jwt ->
              jwt.decoder(token -> {
                // Select only among locally configured issuers. The assertion is verified below.
                var decoder = decoders.get(unverifiedIssuer(token));
                if (decoder == null) {
                  throw new BadJwtException("Invalid Company service assertion");
                }
                return decoder.decode(token);
              }));
        })
        .build();
  }

  private static String unverifiedIssuer(String token) {
    try {
      var issuer = com.nimbusds.jwt.SignedJWT.parse(token).getJWTClaimsSet().getIssuer();
      if (issuer != null) {
        return issuer;
      }
    } catch (java.text.ParseException ignored) {
      // Authentication failures use the same safe response.
    }
    throw new BadJwtException("Invalid Company service assertion");
  }

  private static Map<String, JwtDecoder> serviceDecoders(
      Trust trust, SecurityConfiguration.JwtProperties users) throws IOException {
    var result = new HashMap<String, JwtDecoder>();
    if (trust.callers() == null || trust.callers().isEmpty()) {
      return result;
    }
    var moduli = new HashSet<BigInteger>();
    moduli.add(AccessTokens.verificationKey(users.publicKey()).getModulus());
    for (var entry : trust.callers().entrySet()) {
      var caller = entry.getValue();
      if (caller.issuer() == null
          || caller.issuer().isBlank()
          || caller.issuer().equals(users.issuer())
          || caller.publicKey() == null) {
        throw new IllegalArgumentException("Incomplete Company service trust");
      }
      var key = AccessTokens.verificationKey(caller.publicKey());
      var decoder =
          NimbusJwtDecoder.withPublicKey(key)
              .signatureAlgorithm(SignatureAlgorithm.RS256)
              .validateType(false)
              .build();
      decoder.setJwtValidator(
          new DelegatingOAuth2TokenValidator<>(
              new JwtIssuerValidator(caller.issuer()),
              new JwtTimestampValidator(Duration.ZERO),
              jwt -> validate(jwt, entry.getKey())));
      if (!moduli.add(key.getModulus()) || result.putIfAbsent(caller.issuer(), decoder) != null) {
        throw new IllegalArgumentException("Company service callers require separate credentials");
      }
    }
    return Map.copyOf(result);
  }

  private static OAuth2TokenValidatorResult validate(Jwt jwt, Module module) {
    var issuedAt = jwt.getIssuedAt();
    var expiresAt = jwt.getExpiresAt();
    try {
      if (CompanyAccessProtocol.TOKEN_TYPE.equals(jwt.getHeaders().get("typ"))
          && List.of(CompanyAccessProtocol.AUDIENCE).equals(jwt.getAudience())
          && issuedAt != null
          && expiresAt != null
          && !issuedAt.isAfter(Instant.now())
          && expiresAt.isAfter(Instant.now())
          && expiresAt.isAfter(issuedAt)
          && Duration.between(issuedAt, expiresAt).compareTo(Duration.ofMinutes(1)) <= 0
          && Query.from(jwt).module() == module) {
        return OAuth2TokenValidatorResult.success();
      }
    } catch (IllegalArgumentException invalid) {
      // Malformed authorization context invalidates the technical credential.
    }
    return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
  }

  @ConfigurationProperties("sanigest.company-access")
  record Trust(Map<Module, Caller> callers) {}

  record Caller(String issuer, Resource publicKey) {}
}
