package com.sanigest.security;

import java.io.IOException;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;

public final class AccessTokens {
  private AccessTokens() {}

  public static RSAPublicKey verificationKey(Resource pem) throws IOException {
    try (var input = pem.getInputStream()) {
      var key = RsaKeyConverters.x509().convert(input);
      if (key == null || key.getModulus().bitLength() < 2048) {
        throw new IllegalArgumentException("JWT public key must be RSA with at least 2048 bits");
      }
      return key;
    }
  }

  public static NimbusJwtDecoder servletDecoder(String issuer, String audience, RSAPublicKey key) {
    var decoder =
        NimbusJwtDecoder.withPublicKey(key).signatureAlgorithm(SignatureAlgorithm.RS256).build();
    decoder.setJwtValidator(validators(issuer, audience));
    return decoder;
  }

  public static NimbusReactiveJwtDecoder reactiveDecoder(
      String issuer, String audience, RSAPublicKey key) {
    var decoder =
        NimbusReactiveJwtDecoder.withPublicKey(key)
            .signatureAlgorithm(SignatureAlgorithm.RS256)
            .build();
    decoder.setJwtValidator(validators(issuer, audience));
    return decoder;
  }

  private static OAuth2TokenValidator<Jwt> validators(String issuer, String audience) {
    if (issuer.isBlank() || audience.isBlank()) {
      throw new IllegalArgumentException("JWT issuer and audience are required");
    }
    return new DelegatingOAuth2TokenValidator<>(
        JwtValidators.createDefaultWithIssuer(issuer),
        new JwtTimestampValidator(Duration.ZERO),
        jwt -> validateClaims(jwt, audience));
  }

  static OAuth2TokenValidatorResult validateClaims(Jwt jwt, String audience) {
    var issuedAt = jwt.getIssuedAt();
    var expiresAt = jwt.getExpiresAt();
    if (validSubject(jwt.getSubject())
        && jwt.getAudience() != null
        && jwt.getAudience().contains(audience)
        && issuedAt != null
        && expiresAt != null
        && expiresAt.isAfter(issuedAt)
        && expiresAt.isAfter(Instant.now())
        && !issuedAt.isAfter(Instant.now().plusSeconds(30))) {
      return OAuth2TokenValidatorResult.success();
    }
    return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
  }

  public static boolean validSubject(String subject) {
    try {
      return subject != null && UUID.fromString(subject).toString().equalsIgnoreCase(subject);
    } catch (IllegalArgumentException invalidSubject) {
      return false;
    }
  }
}
