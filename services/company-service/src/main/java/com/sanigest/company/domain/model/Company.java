package com.sanigest.company.domain.model;

import java.util.UUID;

/** Domain representation of a company, independent of persistence and transport. */
public record Company(UUID id, String name, String rnc) {
  public Company {
    if (id == null || name == null || name.isBlank()) {
      throw new IllegalArgumentException("A company requires an id and a name");
    }
  }
}
