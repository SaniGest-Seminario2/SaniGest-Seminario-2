package com.sanigest.identity.infrastructure.persistence.session;

import com.sanigest.identity.application.port.out.persistence.session.RefreshSessionRepository;
import com.sanigest.identity.domain.model.session.RefreshSession;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile({"local", "test"})
class JpaRefreshSessionRepository implements RefreshSessionRepository {
  private final SpringDataRefreshTokenRepository tokens;
  private final EntityManager entities;

  JpaRefreshSessionRepository(SpringDataRefreshTokenRepository tokens, EntityManager entities) {
    this.tokens = tokens;
    this.entities = entities;
  }

  @Override
  public Optional<RefreshSession> findByHashForUpdate(String tokenHash) {
    return tokens.findByTokenHash(tokenHash).map(RefreshTokenJpaEntity::toSession);
  }

  @Override
  public void insert(RefreshSession session) {
    entities.persist(
        new RefreshTokenJpaEntity(
            session.id(),
            session.tokenHash(),
            session.userId(),
            session.familyId(),
            session.expiresAt(),
            session.createdAt()));
  }

  @Override
  public void markUsed(UUID id, Instant now) {
    tokens.getReferenceById(id).use(now);
  }

  @Override
  public void revokeFamily(UUID familyId, Instant now) {
    tokens.findByFamilyId(familyId).forEach(token -> token.revoke(now));
  }

  @Scheduled(fixedDelayString = "${SANIGEST_REFRESH_CLEANUP_DELAY:PT6H}")
  @Transactional
  public void cleanupExpired() {
    tokens.deleteByExpiresAtBefore(Instant.now());
  }
}
