package com.sanigest.identity.application.port.out.persistence;

import com.sanigest.identity.domain.model.RefreshSession;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Refresh session persistence. Locking and writes participate in the caller's transaction. */
public interface RefreshSessionRepository {
  Optional<RefreshSession> findByHashForUpdate(String tokenHash);

  void insert(RefreshSession session);

  void markUsed(UUID id, Instant now);

  void revokeFamily(UUID familyId, Instant now);
}
