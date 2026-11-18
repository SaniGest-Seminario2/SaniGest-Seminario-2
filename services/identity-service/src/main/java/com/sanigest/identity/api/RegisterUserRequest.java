package com.sanigest.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterUserRequest(
    @NotBlank @Size(max = 254) @Email String email,
    @NotNull @Size(min = 12, max = 128) String password,
    @NotBlank @Size(max = 160) String name) {

  public RegisterUserRequest {
    email = strip(email);
    name = strip(name);
  }

  private static String strip(String value) {
    return value == null ? null : value.strip();
  }
}
