package com.sanigest.identity.domain.model;

import java.time.Instant;
import java.util.UUID;

public record RefreshSession(
    UUID id,
    String tokenHash,
    UUID userId,
    UUID familyId,
    Instant expiresAt,
    Instant createdAt,
    Instant usedAt,
    Instant revokedAt) {

  public boolean unusableAt(Instant now) {
    return usedAt != null || revokedAt != null || !expiresAt.isAfter(now);
  }
}
