package com.sanigest.company.api.dto.profile;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CompanyRequest(
    @NotBlank @Size(max = 160) String companyName,
    @NotBlank @Size(max = 160) String businessName,
    @Pattern(regexp = "[0-9]{9}|[0-9]{11}", message = "RNC must contain 9 or 11 digits") String rnc,
    @NotBlank @Email @Size(max = 320) String email,
    @NotBlank @Pattern(regexp = "[0-9+() .-]{7,32}") String phone,
    @NotBlank @Size(max = 255) String location,
    String ownerId,
    String userId) {}
