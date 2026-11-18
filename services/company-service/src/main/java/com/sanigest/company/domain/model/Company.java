package com.sanigest.company.domain.model;

import java.util.UUID;

/** Domain representation of a company, independent of persistence and transport. */
public record Company(
    UUID id,
    String name,
    String businessName,
    String rnc,
    String email,
    String phone,
    String location,
    String currency,
    String status) {
  public Company {
    if (id == null
        || name == null
        || name.isBlank()
        || businessName == null
        || businessName.isBlank()) {
      throw new IllegalArgumentException("A company requires an id and a name");
    }
    if (!"DOP".equals(currency) || !"ACTIVE".equals(status)) {
      throw new IllegalArgumentException("Invalid company defaults");
    }
  }
}
