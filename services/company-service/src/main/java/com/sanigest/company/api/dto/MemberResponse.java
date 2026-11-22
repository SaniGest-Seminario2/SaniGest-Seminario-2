package com.sanigest.company.api.dto;

import com.sanigest.company.domain.enums.CompanyRole;
import com.sanigest.company.domain.enums.MembershipStatus;
import com.sanigest.company.domain.model.Membership;
import java.util.UUID;

public record MemberResponse(
    UUID membershipId, UUID userId, CompanyRole role, MembershipStatus status) {
  public static MemberResponse from(Membership membership) {
    return new MemberResponse(
        membership.id(), membership.userId(), membership.role(), membership.status());
  }
}
