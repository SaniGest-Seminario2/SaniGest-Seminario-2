package com.sanigest.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sanigest.identity.application.AccessToken;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class JwtTokenIssuerTest {

  private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);
  private static final String SECRET =
      Base64.getEncoder().encodeToString("01234567890123456789012345678901".getBytes());
  private final JwtProperties properties =
      new JwtProperties("sanigest", "sanigest-api", SECRET, Duration.ofMinutes(15), Duration.ofDays(30));
  private final SecurityConfiguration configuration = new SecurityConfiguration();

  @Test
  void generatesAValidJwtWithRequiredClaimsAndExpiration() {
    UUID userId = UUID.randomUUID();
    JwtEncoder encoder = configuration.jwtEncoder(properties);
    AccessToken token =
        new JwtTokenIssuer(encoder, properties, Clock.fixed(NOW, ZoneOffset.UTC)).issue(userId);

    var jwt = configuration.jwtDecoder(properties).decode(token.value());

    assertThat(token.type()).isEqualTo("Bearer");
    assertThat(token.expiresIn()).isEqualTo(900);
    assertThat(jwt.getSubject()).isEqualTo(userId.toString());
    assertThat(jwt.getClaimAsString("iss")).isEqualTo("sanigest");
    assertThat(jwt.getAudience()).containsExactly("sanigest-api");
    assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plusSeconds(900));
  }

  @Test
  void rejectsInvalidSignatureIssuerAudienceAndExpiredTokens() {
    JwtDecoder decoder = configuration.jwtDecoder(properties);

    assertThatThrownBy(
            () -> decoder.decode(token("other", "sanigest", "sanigest-api", NOW.plusSeconds(60))))
        .isInstanceOf(JwtException.class);
    assertThatThrownBy(
            () -> decoder.decode(token(SECRET, "other", "sanigest-api", NOW.plusSeconds(60))))
        .isInstanceOf(JwtException.class);
    assertThatThrownBy(
            () -> decoder.decode(token(SECRET, "sanigest", "other", NOW.plusSeconds(60))))
        .isInstanceOf(JwtException.class);
    assertThatThrownBy(
            () -> decoder.decode(token(SECRET, "sanigest", "sanigest-api", Instant.EPOCH)))
        .isInstanceOf(JwtException.class);
  }

  private static String token(String secret, String issuer, String audience, Instant expiresAt) {
    byte[] key =
        secret.equals("other")
            ? "abcdefghijklmnopqrstuvwxyz123456".getBytes()
            : Base64.getDecoder().decode(secret);
    JwtEncoder encoder =
        NimbusJwtEncoder.withSecretKey(new SecretKeySpec(key, "HmacSHA256")).build();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .subject(UUID.randomUUID().toString())
            .issuer(issuer)
            .audience(List.of(audience))
            .issuedAt(expiresAt.minusSeconds(60))
            .expiresAt(expiresAt)
            .build();
    return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
  }
}
