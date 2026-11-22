package com.sanigest.company.domain.model;

import com.sanigest.company.domain.enums.CompanyRole;
import com.sanigest.company.domain.enums.InvitationStatus;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

public record Invitation(
    UUID id,
    UUID companyId,
    String email,
    CompanyRole role,
    InvitationStatus status,
    Instant createdAt,
    Instant expiresAt) {

  public Invitation {
    email = normalizeEmail(email);
    if (id == null
        || companyId == null
        || status == null
        || createdAt == null
        || expiresAt == null
        || !expiresAt.isAfter(createdAt)
        || (role != CompanyRole.OPERATOR && role != CompanyRole.VIEWER)) {
      throw new IllegalArgumentException("Invalid invitation");
    }
  }

  public InvitationStatus statusAt(Instant now) {
    return status == InvitationStatus.PENDING && !now.isBefore(expiresAt)
        ? InvitationStatus.EXPIRED
        : status;
  }

  public boolean isFor(String verifiedEmail) {
    return verifiedEmail != null && email.equals(normalizeEmail(verifiedEmail));
  }

  public static String normalizeEmail(String email) {
    if (email == null) {
      throw new IllegalArgumentException("Invalid invitation email");
    }
    String normalized = email.strip().toLowerCase(Locale.ROOT);
    if (normalized.length() > 320 || !normalized.matches("[^\\s@\\p{Cntrl}]+@[^\\s@\\p{Cntrl}]+")) {
      throw new IllegalArgumentException("Invalid invitation email");
    }
    return normalized;
  }
}
