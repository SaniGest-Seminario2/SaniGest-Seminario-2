package com.sanigest.identity.application.port.out.persistence;

import com.sanigest.identity.domain.model.EmailAddress;
import com.sanigest.identity.domain.model.User;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

  boolean existsByEmail(EmailAddress email);

  Optional<User> findByEmail(EmailAddress email);

  Optional<User> findById(UUID id);

  List<MemberIdentity> findMemberIdentities(Collection<UUID> ids);

  record MemberIdentity(UUID userId, String name, String email) {}

  User save(User user);
}
