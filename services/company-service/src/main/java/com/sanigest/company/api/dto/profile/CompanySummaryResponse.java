package com.sanigest.company.api.dto.profile;

import com.sanigest.company.application.dto.access.AuthorizedCompany;
import com.sanigest.company.domain.enums.team.CompanyRole;
import com.sanigest.company.domain.enums.team.MembershipStatus;
import java.util.UUID;

public record CompanySummaryResponse(
    UUID companyId,
    String companyName,
    String businessName,
    CompanyRole role,
    MembershipStatus membershipStatus,
    String status) {
  public static CompanySummaryResponse from(AuthorizedCompany company) {
    return new CompanySummaryResponse(
        company.companyId(),
        company.companyName(),
        company.businessName(),
        company.role(),
        company.membershipStatus(),
        company.status());
  }
}
