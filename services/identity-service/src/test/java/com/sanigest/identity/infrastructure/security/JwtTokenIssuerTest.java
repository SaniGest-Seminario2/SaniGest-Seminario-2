package com.sanigest.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.sanigest.identity.application.dto.AccessToken;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class JwtTokenIssuerTest {

  @Test
  void generatesAValidJwtWithRequiredClaimsAndExpiration() throws Exception {
    Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    var properties =
        new JwtProperties(
            "sanigest",
            "sanigest-api",
            new ClassPathResource("jwt-test-public.pem"),
            new ClassPathResource("jwt-test-private.pem"),
            Duration.ofMinutes(15));
    var configuration = new SecurityConfiguration();
    var publicKey = configuration.jwtVerificationKey(properties);
    var encoder = configuration.jwtEncoder(properties, publicKey);
    UUID userId = UUID.randomUUID();
    AccessToken token =
        new JwtTokenIssuer(encoder, properties, Clock.fixed(now, ZoneOffset.UTC)).issue(userId);

    var jwt = configuration.jwtDecoder(properties, publicKey).decode(token.value());

    assertThat(token.type()).isEqualTo("Bearer");
    assertThat(token.expiresIn()).isEqualTo(900);
    assertThat(jwt.getHeaders()).containsEntry("alg", "RS256");
    assertThat(jwt.getSubject()).isEqualTo(userId.toString());
    assertThat(jwt.getClaimAsString("iss")).isEqualTo("sanigest");
    assertThat(jwt.getAudience()).containsExactly("sanigest-api");
    assertThat(jwt.getExpiresAt()).isEqualTo(now.plusSeconds(900));
  }
}
