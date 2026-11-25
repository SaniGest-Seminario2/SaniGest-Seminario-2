package com.sanigest.identity.infrastructure.persistence.repository;

import com.sanigest.identity.application.port.out.persistence.UserRepository.MemberIdentity;
import com.sanigest.identity.infrastructure.persistence.entity.UserJpaEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SpringDataUserRepository extends JpaRepository<UserJpaEntity, UUID> {

  @Query(
      """
      select new com.sanigest.identity.application.port.out.persistence.UserRepository$MemberIdentity(
          u.id, u.name, u.email) from UserJpaEntity u where u.id in :ids
      """)
  List<MemberIdentity> findMemberIdentities(Collection<UUID> ids);

  boolean existsByEmail(String email);

  Optional<UserJpaEntity> findByEmail(String email);
}
