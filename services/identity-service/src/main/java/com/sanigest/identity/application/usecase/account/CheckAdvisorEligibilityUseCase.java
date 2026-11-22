package com.sanigest.identity.application.usecase.account;

import com.sanigest.identity.application.port.out.persistence.UserRepository;
import com.sanigest.identity.domain.enums.AccountType;
import com.sanigest.identity.domain.enums.UserStatus;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"local", "test"})
public class CheckAdvisorEligibilityUseCase {
  private final UserRepository users;

  public CheckAdvisorEligibilityUseCase(UserRepository users) {
    this.users = users;
  }

  @Transactional(readOnly = true)
  public boolean isEligible(UUID userId) {
    return users
        .findById(userId)
        .filter(user -> user.status() == UserStatus.ACTIVE)
        .filter(user -> user.accountType() == AccountType.ADVISOR)
        .isPresent();
  }
}
