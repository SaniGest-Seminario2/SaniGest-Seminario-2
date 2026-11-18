package com.sanigest.identity.infrastructure.persistence;

import com.sanigest.identity.domain.model.EmailAddress;
import com.sanigest.identity.domain.model.User;
import com.sanigest.identity.domain.port.DuplicateUserEmailException;
import com.sanigest.identity.domain.port.UserRepository;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "test"})
class JpaUserRepositoryAdapter implements UserRepository {

  private final SpringDataUserRepository repository;

  JpaUserRepositoryAdapter(SpringDataUserRepository repository) {
    this.repository = Objects.requireNonNull(repository);
  }

  @Override
  public boolean existsByEmail(EmailAddress email) {
    return repository.existsByEmail(email.value());
  }

  @Override
  public Optional<User> findByEmail(EmailAddress email) {
    return repository.findByEmail(email.value()).map(JpaUserRepositoryAdapter::toDomain);
  }

  @Override
  public Optional<User> findById(UUID id) {
    return repository.findById(id).map(JpaUserRepositoryAdapter::toDomain);
  }

  @Override
  public User save(User user) {
    try {
      UserJpaEntity saved = repository.saveAndFlush(toEntity(user));
      return toDomain(saved);
    } catch (DataIntegrityViolationException exception) {
      throw new DuplicateUserEmailException(exception);
    }
  }

  private static UserJpaEntity toEntity(User user) {
    return new UserJpaEntity(
        user.id(),
        user.email().value(),
        user.passwordHash(),
        user.name(),
        user.accountType(),
        user.status(),
        user.createdAt(),
        user.updatedAt());
  }

  private static User toDomain(UserJpaEntity entity) {
    return new User(
        entity.getId(),
        EmailAddress.of(entity.getEmail()),
        entity.getPasswordHash(),
        entity.getName(),
        entity.getAccountType(),
        entity.getStatus(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }
}
