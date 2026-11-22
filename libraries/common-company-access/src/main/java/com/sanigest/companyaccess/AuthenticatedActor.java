package com.sanigest.companyaccess;

import com.sanigest.security.AccessTokens;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public final class AuthenticatedActor {
  private AuthenticatedActor() {}

  public static Optional<UUID> userId() {
    return current().map(jwt -> UUID.fromString(jwt.getSubject()));
  }

  static Optional<Jwt> current() {
    if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt
        && jwt.isAuthenticated()
        && jwt.getToken().getExpiresAt() != null
        && jwt.getToken().getExpiresAt().isAfter(Instant.now())
        && AccessTokens.validSubject(jwt.getToken().getSubject())) {
      return Optional.of(jwt.getToken());
    }
    return Optional.empty();
  }

  /** Checks the legacy caller arguments against the authenticated session. */
  public static boolean matches(UUID userId, String bearer) {
    return userId().filter(id -> id.equals(userId)).isPresent()
        && bearer != null
        && SecurityContextHolder.getContext().getAuthentication()
            instanceof JwtAuthenticationToken jwt
        && MessageDigest.isEqual(
            jwt.getToken().getTokenValue().getBytes(StandardCharsets.UTF_8),
            bearer.getBytes(StandardCharsets.UTF_8));
  }
}
