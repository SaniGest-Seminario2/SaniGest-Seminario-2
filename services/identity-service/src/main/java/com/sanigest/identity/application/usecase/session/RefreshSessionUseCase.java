package com.sanigest.identity.application.usecase.session;

import com.sanigest.identity.application.dto.AccessToken;
import com.sanigest.identity.application.dto.IssuedRefreshToken;
import com.sanigest.identity.application.exception.InvalidRefreshTokenException;
import com.sanigest.identity.application.port.in.RefreshTokenService;
import com.sanigest.identity.application.port.out.persistence.RefreshSessionRepository;
import com.sanigest.identity.application.port.out.persistence.UserRepository;
import com.sanigest.identity.application.port.out.security.TokenIssuer;
import com.sanigest.identity.domain.enums.UserStatus;
import com.sanigest.identity.domain.model.RefreshSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"local", "test"})
public class RefreshSessionUseCase implements RefreshTokenService {
  private final RefreshSessionRepository tokens;
  private final UserRepository users;
  private final TokenIssuer issuer;
  private final Duration refreshTokenTtl;
  private final SecureRandom random = new SecureRandom();
  private final Clock clock = Clock.systemUTC();

  RefreshSessionUseCase(
      RefreshSessionRepository tokens,
      UserRepository users,
      TokenIssuer issuer,
      @Value("${sanigest.jwt.refresh-token-ttl}") Duration refreshTokenTtl) {
    this.tokens = Objects.requireNonNull(tokens);
    this.users = Objects.requireNonNull(users);
    this.issuer = Objects.requireNonNull(issuer);
    this.refreshTokenTtl = Objects.requireNonNull(refreshTokenTtl);
  }

  @Override
  @Transactional
  public IssuedRefreshToken issue(UUID userId) {
    Instant now = clock.instant();
    String raw = persistNewToken(userId, UUID.randomUUID(), now);
    return new IssuedRefreshToken(raw, refreshTokenTtl.toSeconds());
  }

  @Override
  @Transactional
  public AccessToken refresh(String rawToken) {
    Instant now = clock.instant();
    RefreshSession current = find(rawToken);
    if (current.unusableAt(now)) {
      tokens.revokeFamily(current.familyId(), now);
      throw new InvalidRefreshTokenException();
    }
    var user = users.findById(current.userId()).orElseThrow(InvalidRefreshTokenException::new);
    if (user.status() != UserStatus.ACTIVE) {
      throw new InvalidRefreshTokenException();
    }
    tokens.markUsed(current.id(), now);
    String raw = persistNewToken(user.id(), current.familyId(), now);
    AccessToken access = issuer.issue(user.id());
    return new AccessToken(
        access.value(), access.type(), access.expiresIn(), raw, refreshTokenTtl.toSeconds());
  }

  @Override
  @Transactional
  public void logout(String rawToken) {
    RefreshSession token = find(rawToken);
    tokens.revokeFamily(token.familyId(), clock.instant());
  }

  private RefreshSession find(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new InvalidRefreshTokenException();
    }
    return tokens.findByHashForUpdate(hash(raw)).orElseThrow(InvalidRefreshTokenException::new);
  }

  private String persistNewToken(UUID userId, UUID familyId, Instant now) {
    String raw = newToken();
    tokens.insert(
        new RefreshSession(
            UUID.randomUUID(),
            hash(raw),
            userId,
            familyId,
            now.plus(refreshTokenTtl),
            now,
            null,
            null));
    return raw;
  }

  private String newToken() {
    byte[] bytes = new byte[48];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String hash(String raw) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Hash unavailable", e);
    }
  }
}
