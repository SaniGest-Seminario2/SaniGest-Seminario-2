package com.sanigest.identity.infrastructure.persistence;

import com.sanigest.identity.application.AccessToken;
import com.sanigest.identity.application.InvalidRefreshTokenException;
import com.sanigest.identity.application.RefreshTokenService;
import com.sanigest.identity.application.TokenIssuer;
import com.sanigest.identity.domain.model.UserStatus;
import com.sanigest.identity.domain.port.UserRepository;
import com.sanigest.identity.infrastructure.security.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"local", "test"})
class JpaRefreshTokenService implements RefreshTokenService {
  private final SpringDataRefreshTokenRepository tokens;
  private final UserRepository users;
  private final TokenIssuer issuer;
  private final JwtProperties properties;
  private final SecureRandom random = new SecureRandom();
  private final Clock clock = Clock.systemUTC();

  JpaRefreshTokenService(
      SpringDataRefreshTokenRepository tokens,
      UserRepository users,
      TokenIssuer issuer,
      JwtProperties properties) {
    this.tokens = Objects.requireNonNull(tokens);
    this.users = Objects.requireNonNull(users);
    this.issuer = Objects.requireNonNull(issuer);
    this.properties = Objects.requireNonNull(properties);
  }

  @Override
  @Transactional
  public IssuedRefreshToken issue(UUID userId) {
    Instant now = clock.instant();
    String raw = newToken();
    tokens.save(
        new RefreshTokenJpaEntity(
            UUID.randomUUID(),
            hash(raw),
            userId,
            UUID.randomUUID(),
            now.plus(properties.refreshTokenTtl()),
            now));
    return new IssuedRefreshToken(raw, properties.refreshTokenTtl().toSeconds());
  }

  @Override
  @Transactional
  public AccessToken refresh(String rawToken) {
    Instant now = clock.instant();
    RefreshTokenJpaEntity current = find(rawToken);
    if (current.getUsedAt() != null
        || current.getRevokedAt() != null
        || !current.getExpiresAt().isAfter(now)) {
      revokeFamily(current.getFamilyId(), now);
      throw new InvalidRefreshTokenException();
    }
    var user = users.findById(current.getUserId()).orElseThrow(InvalidRefreshTokenException::new);
    if (user.status() != UserStatus.ACTIVE) {
      throw new InvalidRefreshTokenException();
    }
    current.use(now);
    String raw = newToken();
    tokens.save(
        new RefreshTokenJpaEntity(
            UUID.randomUUID(),
            hash(raw),
            user.id(),
            current.getFamilyId(),
            now.plus(properties.refreshTokenTtl()),
            now));
    AccessToken access = issuer.issue(user.id());
    return new AccessToken(
        access.value(),
        access.type(),
        access.expiresIn(),
        raw,
        properties.refreshTokenTtl().toSeconds());
  }

  @Override
  @Transactional
  public void logout(String rawToken) {
    RefreshTokenJpaEntity token = find(rawToken);
    revokeFamily(token.getFamilyId(), clock.instant());
  }

  @Scheduled(fixedDelayString = "${SANIGEST_REFRESH_CLEANUP_DELAY:PT6H}")
  @Transactional
  public void cleanupExpired() {
    tokens.deleteByExpiresAtBefore(clock.instant());
  }

  private RefreshTokenJpaEntity find(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new InvalidRefreshTokenException();
    }
    return tokens.findByTokenHash(hash(raw)).orElseThrow(InvalidRefreshTokenException::new);
  }

  private void revokeFamily(UUID family, Instant now) {
    tokens
        .findByFamilyId(family)
        .forEach(t -> {
          t.revoke(now);
        });
  }

  private String newToken() {
    byte[] bytes = new byte[48];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String hash(String raw) {
    try {
      return hex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("Hash unavailable", e);
    }
  }

  private static String hex(byte[] bytes) {
    StringBuilder result = new StringBuilder(64);
    for (byte b : bytes) {
      result.append(String.format("%02x", b));
    }
    return result.toString();
  }
}
