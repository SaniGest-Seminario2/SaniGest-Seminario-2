package com.sanigest.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sanigest.identity.application.AccessToken;
import com.sanigest.identity.application.InvalidRefreshTokenException;
import com.sanigest.identity.application.TokenIssuer;
import com.sanigest.identity.domain.model.AccountType;
import com.sanigest.identity.domain.model.EmailAddress;
import com.sanigest.identity.domain.model.User;
import com.sanigest.identity.domain.model.UserStatus;
import com.sanigest.identity.domain.port.UserRepository;
import com.sanigest.identity.infrastructure.security.JwtProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JpaRefreshTokenServiceTest {
  private static final UUID USER_ID = UUID.randomUUID();
  private static final Instant NOW = Instant.now();

  private final SpringDataRefreshTokenRepository tokens =
      mock(SpringDataRefreshTokenRepository.class);
  private final UserRepository users = mock(UserRepository.class);
  private final TokenIssuer issuer = mock(TokenIssuer.class);
  private final JpaRefreshTokenService service =
      new JpaRefreshTokenService(
          tokens,
          users,
          issuer,
          new JwtProperties(
              "issuer", "audience", "secret", Duration.ofMinutes(15), Duration.ofDays(7)));

  @BeforeEach
  void setUp() {
    when(tokens.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void issuePersistsOnlyTheTokenHash() {
    var issued = service.issue(USER_ID);
    var saved = org.mockito.ArgumentCaptor.forClass(RefreshTokenJpaEntity.class);
    verify(tokens).save(saved.capture());

    assertThat(issued.value()).isNotBlank();
    assertThat(issued.expiresIn()).isEqualTo(Duration.ofDays(7).toSeconds());
    assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
    assertThat(saved.getValue().getExpiresAt()).isAfter(NOW);
  }

  @Test
  void refreshRotatesTokenAndIssuesAccessToken() {
    UUID familyId = UUID.randomUUID();
    RefreshTokenJpaEntity current = token("old-token", familyId, Instant.now().plusSeconds(60));
    when(tokens.findByTokenHash(any())).thenReturn(Optional.of(current));
    when(users.findById(USER_ID)).thenReturn(Optional.of(user(UserStatus.ACTIVE)));
    when(issuer.issue(USER_ID)).thenReturn(new AccessToken("access", "Bearer", 900));

    AccessToken refreshed = service.refresh("old-token");

    assertThat(current.getUsedAt()).isNotNull();
    assertThat(refreshed.value()).isEqualTo("access");
    assertThat(refreshed.refreshToken()).isNotEqualTo("old-token");
    verify(tokens).save(any());
  }

  @Test
  void rejectsBlankAndUnknownTokens() {
    assertThatThrownBy(() -> service.refresh(" ")).isInstanceOf(InvalidRefreshTokenException.class);
    when(tokens.findByTokenHash(any())).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.refresh("unknown"))
        .isInstanceOf(InvalidRefreshTokenException.class);
  }

  @Test
  void reusedTokenRevokesItsFamily() {
    UUID familyId = UUID.randomUUID();
    RefreshTokenJpaEntity used = token("used-token", familyId, Instant.now().plusSeconds(60));
    used.use(Instant.now());
    RefreshTokenJpaEntity sibling = token("sibling", familyId, Instant.now().plusSeconds(60));
    when(tokens.findByTokenHash(any())).thenReturn(Optional.of(used));
    when(tokens.findByFamilyId(familyId)).thenReturn(List.of(used, sibling));

    assertThatThrownBy(() -> service.refresh("used-token"))
        .isInstanceOf(InvalidRefreshTokenException.class);
    assertThat(used.getRevokedAt()).isNotNull();
    assertThat(sibling.getRevokedAt()).isNotNull();
  }

  @Test
  void rejectsExpiredTokensAndRevokesTheirFamily() {
    UUID familyId = UUID.randomUUID();
    RefreshTokenJpaEntity expired = token("expired", familyId, Instant.EPOCH);
    when(tokens.findByTokenHash(any())).thenReturn(Optional.of(expired));
    when(tokens.findByFamilyId(familyId)).thenReturn(List.of(expired));

    assertThatThrownBy(() -> service.refresh("expired"))
        .isInstanceOf(InvalidRefreshTokenException.class);
    assertThat(expired.getRevokedAt()).isNotNull();
  }

  @Test
  void rejectsMissingOrDisabledUsers() {
    RefreshTokenJpaEntity token = token("token", UUID.randomUUID(), Instant.now().plusSeconds(60));
    when(tokens.findByTokenHash(any())).thenReturn(Optional.of(token));
    when(users.findById(USER_ID)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.refresh("token"))
        .isInstanceOf(InvalidRefreshTokenException.class);

    when(users.findById(USER_ID)).thenReturn(Optional.of(user(UserStatus.DISABLED)));
    assertThatThrownBy(() -> service.refresh("token"))
        .isInstanceOf(InvalidRefreshTokenException.class);
  }

  @Test
  void logoutRevokesAllFamilyTokensAndCleanupDeletesExpiredTokens() {
    UUID familyId = UUID.randomUUID();
    RefreshTokenJpaEntity token = token("token", familyId, Instant.now().plusSeconds(60));
    RefreshTokenJpaEntity sibling = token("sibling", familyId, Instant.now().plusSeconds(60));
    when(tokens.findByTokenHash(any())).thenReturn(Optional.of(token));
    when(tokens.findByFamilyId(familyId)).thenReturn(List.of(token, sibling));

    service.logout("token");
    service.cleanupExpired();

    assertThat(token.getRevokedAt()).isNotNull();
    assertThat(sibling.getRevokedAt()).isNotNull();
    verify(tokens).deleteByExpiresAtBefore(any());
  }

  private static RefreshTokenJpaEntity token(String raw, UUID familyId, Instant expiresAt) {
    return new RefreshTokenJpaEntity(UUID.randomUUID(), raw, USER_ID, familyId, expiresAt, NOW);
  }

  private static User user(UserStatus status) {
    return new User(
        USER_ID,
        EmailAddress.of("user@example.com"),
        "hash",
        "User",
        AccountType.REGULAR,
        status,
        NOW,
        NOW);
  }
}
