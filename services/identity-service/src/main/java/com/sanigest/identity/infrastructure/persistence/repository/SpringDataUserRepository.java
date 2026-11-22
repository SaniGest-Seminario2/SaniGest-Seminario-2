package com.sanigest.identity.infrastructure.persistence.repository;

import com.sanigest.identity.infrastructure.persistence.entity.UserJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataUserRepository extends JpaRepository<UserJpaEntity, UUID> {

  boolean existsByEmail(String email);

  Optional<UserJpaEntity> findByEmail(String email);
}
