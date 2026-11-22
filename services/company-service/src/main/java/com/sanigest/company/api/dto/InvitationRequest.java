package com.sanigest.company.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record InvitationRequest(
    @NotBlank @Email @Size(max = 320) String email,
    @NotBlank @Pattern(regexp = "OPERATOR|VIEWER") String role,
    boolean replace) {}
