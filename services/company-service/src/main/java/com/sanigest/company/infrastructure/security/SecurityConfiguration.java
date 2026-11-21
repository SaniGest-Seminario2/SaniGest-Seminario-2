package com.sanigest.company.infrastructure.security;

import jakarta.servlet.DispatcherType;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityConfiguration.JwtProperties.class)
class SecurityConfiguration {
  @Bean
  SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(a ->
            a.dispatcherTypeMatchers(DispatcherType.ERROR)
                .permitAll()
                .requestMatchers("/actuator/**", "/v3/api-docs", "/swagger-ui.html")
                .permitAll()
                .anyRequest()
                .authenticated())
        .oauth2ResourceServer(o -> o.jwt(j -> {}))
        .build();
  }

  @Bean
  JwtDecoder jwtDecoder(JwtProperties p) {
    var decoder =
        NimbusJwtDecoder.withSecretKey(
                new SecretKeySpec(p.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
            .build();
    OAuth2TokenValidator<Jwt> audience =
        jwt ->
            jwt.getAudience() != null && jwt.getAudience().contains(p.audience())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(p.issuer()),
            audience,
            this::validateAccessTokenClaims));
    return decoder;
  }

  private OAuth2TokenValidatorResult validateAccessTokenClaims(Jwt jwt) {
    String subject = jwt.getSubject();
    var expiresAt = jwt.getExpiresAt();
    if (subject != null && expiresAt != null && expiresAt.isAfter(Instant.now())) {
      try {
        if (UUID.fromString(subject).toString().equalsIgnoreCase(subject)) {
          return OAuth2TokenValidatorResult.success();
        }
      } catch (IllegalArgumentException ignored) {
        // An invalid subject cannot identify an authenticated user.
      }
    }
    return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
  }

  @ConfigurationProperties("sanigest.jwt")
  record JwtProperties(String issuer, String audience, String secret) {}
}
