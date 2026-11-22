package com.sanigest.catalog.domain;

public record ClientDetails(String name, String taxId, String email, String phone, String address) {
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
