package com.sanigest.company.application.dto.team;

import com.sanigest.company.application.dto.profile.CompanyProfile;
import com.sanigest.company.domain.enums.team.MembershipStatus;

public record CompanyMembership(MembershipStatus status, CompanyProfile company) {}
