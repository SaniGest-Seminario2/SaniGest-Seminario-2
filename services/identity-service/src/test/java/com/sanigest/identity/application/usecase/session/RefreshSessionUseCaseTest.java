package com.sanigest.identity.application.usecase.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sanigest.identity.application.dto.AccessToken;
import com.sanigest.identity.application.exception.InvalidRefreshTokenException;
import com.sanigest.identity.application.port.out.persistence.RefreshSessionRepository;
import com.sanigest.identity.application.port.out.persistence.UserRepository;
import com.sanigest.identity.application.port.out.security.TokenIssuer;
import com.sanigest.identity.domain.enums.AccountType;
import com.sanigest.identity.domain.enums.UserStatus;
import com.sanigest.identity.domain.model.EmailAddress;
import com.sanigest.identity.domain.model.RefreshSession;
import com.sanigest.identity.domain.model.User;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefreshSessionUseCaseTest {
  private static final UUID USER_ID = UUID.randomUUID();
  private static final Instant NOW = Instant.now();

  private final RefreshSessionRepository tokens = mock(RefreshSessionRepository.class);
  private final UserRepository users = mock(UserRepository.class);
  private final TokenIssuer issuer = mock(TokenIssuer.class);
  private final RefreshSessionUseCase service =
      new RefreshSessionUseCase(tokens, users, issuer, Duration.ofDays(7));

  @Test
  void issuePersistsOnlyTheTokenHash() {
    var issued = service.issue(USER_ID);
    var saved = org.mockito.ArgumentCaptor.forClass(RefreshSession.class);
    verify(tokens).insert(saved.capture());

    assertThat(issued.value()).isNotBlank();
    assertThat(issued.expiresIn()).isEqualTo(Duration.ofDays(7).toSeconds());
    assertThat(saved.getValue().tokenHash()).hasSize(64).isNotEqualTo(issued.value());
    assertThat(saved.getValue().userId()).isEqualTo(USER_ID);
    assertThat(saved.getValue().expiresAt()).isAfter(NOW);
  }

  @Test
  void refreshRotatesTokenAndIssuesAccessToken() {
    UUID familyId = UUID.randomUUID();
    RefreshSession current = token("old-token", familyId, Instant.now().plusSeconds(60));
    when(tokens.findByHashForUpdate(any())).thenReturn(Optional.of(current));
    when(users.findById(USER_ID)).thenReturn(Optional.of(user(UserStatus.ACTIVE)));
    when(issuer.issue(USER_ID)).thenReturn(new AccessToken("access", "Bearer", 900));

    AccessToken refreshed = service.refresh("old-token");

    verify(tokens).markUsed(org.mockito.ArgumentMatchers.eq(current.id()), any());
    assertThat(refreshed.value()).isEqualTo("access");
    assertThat(refreshed.refreshToken()).isNotEqualTo("old-token");
    verify(tokens).insert(any());
  }

  @Test
  void rejectsBlankAndUnknownTokens() {
    assertThatThrownBy(() -> service.refresh(" ")).isInstanceOf(InvalidRefreshTokenException.class);
    when(tokens.findByHashForUpdate(any())).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.refresh("unknown"))
        .isInstanceOf(InvalidRefreshTokenException.class);
  }

  @Test
  void reusedTokenRevokesItsFamily() {
    UUID familyId = UUID.randomUUID();
    var used =
        new RefreshSession(
            UUID.randomUUID(),
            "used-token",
            USER_ID,
            familyId,
            NOW.plusSeconds(60),
            NOW,
            NOW,
            null);
    when(tokens.findByHashForUpdate(any())).thenReturn(Optional.of(used));

    assertThatThrownBy(() -> service.refresh("used-token"))
        .isInstanceOf(InvalidRefreshTokenException.class);
    verify(tokens).revokeFamily(org.mockito.ArgumentMatchers.eq(familyId), any());
  }

  @Test
  void rejectsExpiredTokensAndRevokesTheirFamily() {
    UUID familyId = UUID.randomUUID();
    RefreshSession expired = token("expired", familyId, Instant.EPOCH);
    when(tokens.findByHashForUpdate(any())).thenReturn(Optional.of(expired));

    assertThatThrownBy(() -> service.refresh("expired"))
        .isInstanceOf(InvalidRefreshTokenException.class);
    verify(tokens).revokeFamily(org.mockito.ArgumentMatchers.eq(familyId), any());
  }

  @Test
  void rejectsMissingOrDisabledUsers() {
    RefreshSession token = token("token", UUID.randomUUID(), Instant.now().plusSeconds(60));
    when(tokens.findByHashForUpdate(any())).thenReturn(Optional.of(token));
    when(users.findById(USER_ID)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.refresh("token"))
        .isInstanceOf(InvalidRefreshTokenException.class);

    when(users.findById(USER_ID)).thenReturn(Optional.of(user(UserStatus.DISABLED)));
    assertThatThrownBy(() -> service.refresh("token"))
        .isInstanceOf(InvalidRefreshTokenException.class);
  }

  @Test
  void logoutRevokesAllFamilyTokens() {
    UUID familyId = UUID.randomUUID();
    RefreshSession token = token("token", familyId, Instant.now().plusSeconds(60));
    when(tokens.findByHashForUpdate(any())).thenReturn(Optional.of(token));

    service.logout("token");
    verify(tokens).revokeFamily(org.mockito.ArgumentMatchers.eq(familyId), any());
  }

  private static RefreshSession token(String raw, UUID familyId, Instant expiresAt) {
    return new RefreshSession(
        UUID.randomUUID(), raw, USER_ID, familyId, expiresAt, NOW, null, null);
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
