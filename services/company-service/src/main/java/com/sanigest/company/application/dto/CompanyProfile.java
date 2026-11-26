package com.sanigest.company.application.dto;

import java.util.UUID;

public record CompanyProfile(
    UUID companyId,
    String companyName,
    String businessName,
    String rnc,
    String location,
    String currency,
    String status,
    String email,
    String phone) {}
