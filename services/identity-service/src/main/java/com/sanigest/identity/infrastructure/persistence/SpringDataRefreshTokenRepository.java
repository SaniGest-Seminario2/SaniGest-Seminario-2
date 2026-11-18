package com.sanigest.identity.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface SpringDataRefreshTokenRepository extends JpaRepository<RefreshTokenJpaEntity, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<RefreshTokenJpaEntity> findByTokenHash(String tokenHash);
  @Query("select t from RefreshTokenJpaEntity t where t.familyId = ?1")
  List<RefreshTokenJpaEntity> findByFamilyId(UUID familyId);
  long deleteByExpiresAtBefore(java.time.Instant instant);
}
