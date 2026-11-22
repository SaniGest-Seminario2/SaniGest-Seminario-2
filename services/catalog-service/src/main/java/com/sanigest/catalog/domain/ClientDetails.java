package com.sanigest.catalog.domain;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ClientDetails(
    @NotBlank @Size(max = 160) String name,
    @Size(max = 64) String taxId,
    @Email @Size(max = 320) String email,
    @Pattern(regexp = "[0-9+() .-]{7,32}") String phone,
    @Size(max = 255) String address) {
  public ClientDetails {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Client name is required");
    }
    name = name.strip();
    if (taxId != null) {
      taxId = taxId.replaceAll("^\\s+|\\s+\\z", "");
      if (taxId.isEmpty()) {
        taxId = null;
      }
    }
  }
}
