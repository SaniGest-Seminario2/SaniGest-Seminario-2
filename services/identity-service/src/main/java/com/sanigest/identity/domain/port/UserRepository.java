package com.sanigest.identity.domain.port;

import com.sanigest.identity.domain.model.EmailAddress;
import com.sanigest.identity.domain.model.User;

public interface UserRepository {

  boolean existsByEmail(EmailAddress email);

  User save(User user);
}
