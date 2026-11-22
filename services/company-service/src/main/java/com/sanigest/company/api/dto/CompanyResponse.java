package com.sanigest.company.api.dto;

import java.util.UUID;

public record CompanyResponse(
    UUID companyId,
    String companyName,
    String businessName,
    String rnc,
    String email,
    String phone,
    String location,
    String currency,
    String status) {}
