package com.sanigest.identity.application.usecase.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sanigest.identity.application.port.out.persistence.UserRepository;
import com.sanigest.identity.domain.enums.AccountType;
import com.sanigest.identity.domain.enums.UserStatus;
import com.sanigest.identity.domain.model.EmailAddress;
import com.sanigest.identity.domain.model.User;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CheckAdvisorEligibilityUseCaseTest {
  private final UserRepository users = mock(UserRepository.class);
  private final CheckAdvisorEligibilityUseCase useCase = new CheckAdvisorEligibilityUseCase(users);
  private final UUID userId = UUID.randomUUID();

  @ParameterizedTest
  @CsvSource({"ADVISOR, ACTIVE, true", "ADVISOR, DISABLED, false", "REGULAR, ACTIVE, false"})
  void returnsOnlyTheEligibilityDecision(AccountType type, UserStatus status, boolean eligible) {
    var user =
        new User(
            userId,
            new EmailAddress("fixture@example.com"),
            "fixture-hash",
            "Fixture",
            type,
            status,
            Instant.EPOCH,
            Instant.EPOCH);
    when(users.findById(userId)).thenReturn(Optional.of(user));

    assertThat(useCase.isEligible(userId)).isEqualTo(eligible);
  }

  @Test
  void returnsFalseForAnUnknownUser() {
    when(users.findById(userId)).thenReturn(Optional.empty());

    assertThat(useCase.isEligible(userId)).isFalse();
  }
}
