package com.sanigest.identity.application;

import com.sanigest.identity.domain.model.User;
import com.sanigest.identity.domain.model.UserStatus;
import com.sanigest.identity.domain.port.UserRepository;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"local", "test"})
public class GetAuthenticatedUserProfileService {

  private final UserRepository users;

  public GetAuthenticatedUserProfileService(UserRepository users) {
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
