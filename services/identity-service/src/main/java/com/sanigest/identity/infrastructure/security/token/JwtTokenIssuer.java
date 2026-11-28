package com.sanigest.identity.infrastructure.security.token;

import com.sanigest.identity.application.dto.session.AccessToken;
import com.sanigest.identity.application.port.out.security.token.TokenIssuer;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

public final class JwtTokenIssuer implements TokenIssuer {

  private final JwtEncoder encoder;
  private final JwtProperties properties;
  private final Clock clock;

  public JwtTokenIssuer(JwtEncoder encoder, JwtProperties properties) {
    this(encoder, properties, Clock.systemUTC());
  }

  public JwtTokenIssuer(JwtEncoder encoder, JwtProperties properties, Clock clock) {
    this.encoder = Objects.requireNonNull(encoder);
    this.properties = Objects.requireNonNull(properties);
    this.clock = Objects.requireNonNull(clock);
  }

  @Override
  public AccessToken issue(UUID userId) {
    Instant issuedAt = clock.instant();
    Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .subject(userId.toString())
            .issuer(properties.issuer())
            .audience(java.util.List.of(properties.audience()))
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .build();
    String value = encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    return new AccessToken(value, "Bearer", properties.accessTokenTtl().toSeconds());
  }
}
