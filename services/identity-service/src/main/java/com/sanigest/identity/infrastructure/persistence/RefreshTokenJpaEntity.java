package com.sanigest.identity.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
class RefreshTokenJpaEntity {
  @Id private UUID id;

  @Column(name = "token_hash", nullable = false, unique = true, length = 64)
  private String tokenHash;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "family_id", nullable = false)
  private UUID familyId;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "used_at")
  private Instant usedAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  protected RefreshTokenJpaEntity() {}

  RefreshTokenJpaEntity(
      UUID id, String hash, UUID userId, UUID familyId, Instant expiresAt, Instant now) {
    this.id = id;
    this.tokenHash = hash;
    this.userId = userId;
    this.familyId = familyId;
    this.expiresAt = expiresAt;
    this.createdAt = now;
  }

  UUID getUserId() {
    return userId;
  }

  UUID getFamilyId() {
    return familyId;
  }

  Instant getExpiresAt() {
    return expiresAt;
  }

  Instant getUsedAt() {
    return usedAt;
  }

  Instant getRevokedAt() {
    return revokedAt;
  }

  void use(Instant now) {
    usedAt = now;
  }

  void revoke(Instant now) {
    revokedAt = now;
  }
}
