package com.sanigest.company.application.dto;

import com.sanigest.company.domain.enums.CompanyRole;
import com.sanigest.company.domain.enums.MembershipStatus;
import java.util.UUID;

public record AuthorizedCompany(
    UUID companyId,
    String companyName,
    String businessName,
    CompanyRole role,
    MembershipStatus membershipStatus,
    String status) {}
