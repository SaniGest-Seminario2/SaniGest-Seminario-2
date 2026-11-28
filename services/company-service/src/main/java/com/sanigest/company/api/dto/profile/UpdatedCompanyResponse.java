package com.sanigest.company.api.dto.profile;

import java.time.Instant;
import java.util.UUID;

public record UpdatedCompanyResponse(
    UUID companyId,
    String companyName,
    String businessName,
    String rnc,
    String email,
    String phone,
    String location,
    String currency,
    String status,
    Instant updatedAt) {}
