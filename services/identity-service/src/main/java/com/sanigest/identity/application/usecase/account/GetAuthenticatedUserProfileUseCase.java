package com.sanigest.identity.application.usecase.account;

import com.sanigest.identity.application.dto.AuthenticatedUserProfile;
import com.sanigest.identity.application.exception.AuthenticatedUserNotFoundException;
import com.sanigest.identity.application.port.out.persistence.UserRepository;
import com.sanigest.identity.domain.enums.UserStatus;
import com.sanigest.identity.domain.model.User;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"local", "test"})
public class GetAuthenticatedUserProfileUseCase {

  private final UserRepository users;

  public GetAuthenticatedUserProfileUseCase(UserRepository users) {
    this.users = Objects.requireNonNull(users);
  }

  @Transactional(readOnly = true)
  public AuthenticatedUserProfile get(String subject) {
    UUID userId;
    try {
      userId = UUID.fromString(subject);
    } catch (IllegalArgumentException | NullPointerException exception) {
      throw new AuthenticatedUserNotFoundException();
    }

    User user = users.findById(userId).orElseThrow(AuthenticatedUserNotFoundException::new);
    if (user.status() != UserStatus.ACTIVE) {
      throw new AuthenticatedUserNotFoundException();
    }
    return new AuthenticatedUserProfile(
        user.id(), user.name(), user.email().value(), user.accountType().name());
  }
}
