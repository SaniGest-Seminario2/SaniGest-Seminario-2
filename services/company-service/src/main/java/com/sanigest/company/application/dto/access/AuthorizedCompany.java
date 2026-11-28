package com.sanigest.company.application.dto.access;

import com.sanigest.company.domain.enums.team.CompanyRole;
import com.sanigest.company.domain.enums.team.MembershipStatus;
import java.util.UUID;

public record AuthorizedCompany(
    UUID companyId,
    String companyName,
    String businessName,
    CompanyRole role,
    MembershipStatus membershipStatus,
    String status) {}
