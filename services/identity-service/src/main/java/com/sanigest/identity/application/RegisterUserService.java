package com.sanigest.identity.application;

import com.sanigest.identity.domain.model.EmailAddress;
import com.sanigest.identity.domain.model.User;
import com.sanigest.identity.domain.port.DuplicateUserEmailException;
import com.sanigest.identity.domain.port.PasswordHasher;
import com.sanigest.identity.domain.port.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"local", "test"})
public class RegisterUserService {

  private static final int MIN_PASSWORD_LENGTH = 12;
  private final UserRepository userRepository;
  private final PasswordHasher passwordHasher;
  private final Clock clock;
  private final Supplier<UUID> idSupplier;

  @Autowired
  public RegisterUserService(UserRepository userRepository, PasswordHasher passwordHasher) {
    this(userRepository, passwordHasher, Clock.systemUTC(), UUID::randomUUID);
  }

  RegisterUserService(
      UserRepository userRepository,
      PasswordHasher passwordHasher,
      Clock clock,
      Supplier<UUID> idSupplier) {
    this.userRepository = Objects.requireNonNull(userRepository);
    this.passwordHasher = Objects.requireNonNull(passwordHasher);
    this.clock = Objects.requireNonNull(clock);
    this.idSupplier = Objects.requireNonNull(idSupplier);
  }

  @Transactional
  public User register(RegisterUserCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    EmailAddress email = EmailAddress.of(command.email());
    validatePassword(command.password());
    if (userRepository.existsByEmail(email)) {
      throw new DuplicateEmailException();
    }

    Instant now = clock.instant();
    User user =
        User.register(
            idSupplier.get(), email, passwordHasher.hash(command.password()), command.name(), now);
    try {
      return userRepository.save(user);
    } catch (DuplicateUserEmailException exception) {
      throw new DuplicateEmailException();
    }
  }

  private static void validatePassword(String password) {
    if (password == null || password.length() < MIN_PASSWORD_LENGTH || password.length() > 128) {
      throw new IllegalArgumentException("password must contain between 12 and 128 characters");
    }
  }
}
