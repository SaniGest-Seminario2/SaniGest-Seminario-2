package com.sanigest.company.api.dto.profile;

import java.util.UUID;

public record CompanyProfileResponse(
    UUID companyId,
    String companyName,
    String businessName,
    String rnc,
    String location,
    String currency,
    String status,
    String email,
    String phone) {}
