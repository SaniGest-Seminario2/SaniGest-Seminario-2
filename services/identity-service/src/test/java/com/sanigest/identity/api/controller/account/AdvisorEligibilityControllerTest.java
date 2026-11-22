package com.sanigest.identity.api.controller.account;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sanigest.identity.application.usecase.account.CheckAdvisorEligibilityUseCase;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AdvisorEligibilityControllerTest {
  @ParameterizedTest
  @ValueSource(strings = {"not-a-uuid", "1-2-3-4-5"})
  void rejectsAnInvalidUuidBeforeLookingUpAnAccount(String userId) {
    var useCase = mock(CheckAdvisorEligibilityUseCase.class);
    var controller = new AdvisorEligibilityController(useCase);

    assertThatThrownBy(() -> controller.check(userId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("userId must be a UUID");
    verifyNoInteractions(useCase);
  }
}
