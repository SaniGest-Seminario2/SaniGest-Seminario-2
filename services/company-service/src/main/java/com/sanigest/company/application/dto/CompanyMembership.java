package com.sanigest.company.application.dto;

import com.sanigest.company.domain.enums.MembershipStatus;

public record CompanyMembership(MembershipStatus status, CompanyProfile company) {}
