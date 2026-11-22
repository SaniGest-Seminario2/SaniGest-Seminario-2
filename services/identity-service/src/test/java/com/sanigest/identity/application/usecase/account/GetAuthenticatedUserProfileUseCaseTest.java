package com.sanigest.identity.application.usecase.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.sanigest.identity.application.dto.AuthenticatedUserProfile;
import com.sanigest.identity.application.exception.AuthenticatedUserNotFoundException;
import com.sanigest.identity.application.port.out.persistence.UserRepository;
import com.sanigest.identity.domain.enums.AccountType;
import com.sanigest.identity.domain.enums.UserStatus;
import com.sanigest.identity.domain.model.EmailAddress;
import com.sanigest.identity.domain.model.User;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetAuthenticatedUserProfileUseCaseTest {

  private static final UUID USER_ID = UUID.fromString("345cb50f-314a-4d0d-9803-26256c1df381");
  private static final Instant NOW = Instant.parse("2026-11-18T12:00:00Z");

  @Mock private UserRepository users;
  private GetAuthenticatedUserProfileUseCase service;

  @BeforeEach
  void setUp() {
    service = new GetAuthenticatedUserProfileUseCase(users);
  }

  @Test
  void returnsTheActiveUserResolvedFromTheJwtSubject() {
    when(users.findById(USER_ID)).thenReturn(Optional.of(user(UserStatus.ACTIVE)));

    AuthenticatedUserProfile profile = service.get(USER_ID.toString());

    assertThat(profile)
        .isEqualTo(
            new AuthenticatedUserProfile(
                USER_ID, "Profile User", "profile@example.com", "REGULAR"));
  }

  @Test
  void rejectsAnUnknownAuthenticatedUser() {
    when(users.findById(USER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.get(USER_ID.toString()))
        .isInstanceOf(AuthenticatedUserNotFoundException.class);
  }

  @Test
  void rejectsADisabledAuthenticatedUser() {
    when(users.findById(USER_ID)).thenReturn(Optional.of(user(UserStatus.DISABLED)));

    assertThatThrownBy(() -> service.get(USER_ID.toString()))
        .isInstanceOf(AuthenticatedUserNotFoundException.class);
  }

  private static User user(UserStatus status) {
    return new User(
        USER_ID,
        EmailAddress.of("profile@example.com"),
        "stored-password-hash",
        "Profile User",
        AccountType.REGULAR,
        status,
        NOW,
        NOW);
  }
}
