package com.sanigest.catalog.api.dto;

import com.sanigest.catalog.domain.ClientDetails;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateClientRequest(
    @NotBlank @Size(max = 160) String name,
    @Size(max = 64) String taxId,
    @Email @Size(max = 320) String email,
    @Pattern(regexp = "[0-9+() .-]{7,32}") String phone,
    @Size(max = 255) String address) {
  public ClientDetails toDetails() {
    return new ClientDetails(name, taxId, email, phone, address);
  }
}
