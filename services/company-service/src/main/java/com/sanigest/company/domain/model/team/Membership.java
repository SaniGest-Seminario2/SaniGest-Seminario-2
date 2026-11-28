package com.sanigest.company.domain.model.team;

import com.sanigest.company.domain.enums.team.CompanyRole;
import com.sanigest.company.domain.enums.team.MembershipStatus;
import java.util.UUID;

/** Membership references Identity by UUID and never by a shared entity or repository. */
public record Membership(
    UUID id, UUID companyId, UUID userId, CompanyRole role, MembershipStatus status) {
  public Membership {
    if (id == null || companyId == null || userId == null || role == null || status == null) {
      throw new IllegalArgumentException("A membership requires all identity fields");
    }
  }
}
