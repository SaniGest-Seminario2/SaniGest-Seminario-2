package com.sanigest.identity.application.usecase.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sanigest.identity.application.dto.session.AccessToken;
import com.sanigest.identity.application.exception.session.InvalidCredentialsException;
import com.sanigest.identity.application.port.out.persistence.account.UserRepository;
import com.sanigest.identity.application.port.out.security.password.PasswordHasher;
import com.sanigest.identity.application.port.out.security.token.TokenIssuer;
import com.sanigest.identity.domain.enums.AccountType;
import com.sanigest.identity.domain.enums.UserStatus;
import com.sanigest.identity.domain.model.account.EmailAddress;
import com.sanigest.identity.domain.model.account.User;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoginUseCaseTest {

  private final UserRepository users = mock(UserRepository.class);
  private final PasswordHasher passwords = mock(PasswordHasher.class);
  private final TokenIssuer tokens = mock(TokenIssuer.class);
  private final LoginUseCase service = new LoginUseCase(users, passwords, tokens);
  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void configureUser() {
    when(users.findByEmail(EmailAddress.of("user@example.com")))
        .thenReturn(Optional.of(user(UserStatus.ACTIVE)));
  }

  @Test
  void authenticatesWithTheExistingPasswordHasherAndIssuesAToken() {
    when(passwords.matches("correct-password", "stored-hash")).thenReturn(true);
    when(tokens.issue(userId)).thenReturn(new AccessToken("jwt", "Bearer", 300));

    AccessToken result = service.login(" USER@example.com ", "correct-password");

    assertThat(result).isEqualTo(new AccessToken("jwt", "Bearer", 300));
    verify(tokens).issue(userId);
  }

  @Test
  void rejectsUnknownUsersWithTheSameGenericError() {
    when(users.findByEmail(EmailAddress.of("missing@example.com"))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.login("missing@example.com", "anything"))
        .isInstanceOf(InvalidCredentialsException.class)
        .hasMessage("Invalid credentials");
  }

  @Test
  void rejectsWrongPasswordsWithTheSameGenericError() {
    when(passwords.matches("wrong-password", "stored-hash")).thenReturn(false);

    assertThatThrownBy(() -> service.login("user@example.com", "wrong-password"))
        .isInstanceOf(InvalidCredentialsException.class)
        .hasMessage("Invalid credentials");
  }

  @Test
  void rejectsInactiveUsersWithoutIssuingAToken() {
    when(users.findByEmail(EmailAddress.of("user@example.com")))
        .thenReturn(Optional.of(user(UserStatus.DISABLED)));

    assertThatThrownBy(() -> service.login("user@example.com", "correct-password"))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  private User user(UserStatus status) {
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    return new User(
        userId,
        EmailAddress.of("user@example.com"),
        "stored-hash",
        "User",
        AccountType.REGULAR,
        status,
        now,
        now);
  }
}
