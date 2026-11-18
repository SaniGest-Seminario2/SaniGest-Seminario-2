package com.sanigest.identity.domain.port;

import com.sanigest.identity.domain.model.EmailAddress;
import com.sanigest.identity.domain.model.User;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

  boolean existsByEmail(EmailAddress email);

  Optional<User> findByEmail(EmailAddress email);

  Optional<User> findById(UUID id);

  User save(User user);
}
