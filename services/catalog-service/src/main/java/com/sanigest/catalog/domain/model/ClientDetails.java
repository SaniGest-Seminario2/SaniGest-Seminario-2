package com.sanigest.catalog.domain.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ClientDetails(
    @NotBlank @Size(max = 160) String name,
    @Size(max = 64) String taxId,
    @Email @Size(max = 320) String email,
    @Pattern(regexp = "[0-9+() .-]{7,32}") String phone,
    @Size(max = 255) String address,
    @Valid ClientProfile profile) {
  public ClientDetails(String name, String taxId, String email, String phone, String address) {
    this(name, taxId, email, phone, address, null);
  }

  public ClientDetails withProfile(ClientProfile updated) {
    return new ClientDetails(name, updated.normalizeTaxId(taxId), email, phone, address, updated);
  }

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
