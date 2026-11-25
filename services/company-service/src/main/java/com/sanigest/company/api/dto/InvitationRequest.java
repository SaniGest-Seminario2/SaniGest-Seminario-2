package com.sanigest.company.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record InvitationRequest(
    @NotBlank @Email @Size(max = 320) String email,
    @NotBlank @Pattern(regexp = "OPERATOR|VIEWER") String role,
    Boolean replace,
    @Size(max = 160) String name) {
  public InvitationRequest {
    email = email == null ? null : email.strip();
    name = name == null ? null : name.strip();
    replace = Boolean.TRUE.equals(replace);
  }
}
