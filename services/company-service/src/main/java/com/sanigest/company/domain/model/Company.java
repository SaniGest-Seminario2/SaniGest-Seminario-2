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
  public Company updateProfile(ProfilePatch patch) {
    return new Company(
        id,
        name,
        patch.businessName() == null ? businessName : patch.businessName(),
        patch.rncProvided() ? patch.rnc() : rnc,
        patch.email() == null ? email : patch.email(),
        patch.phone() == null ? phone : patch.phone(),
        patch.location() == null ? location : patch.location(),
        currency,
        status);
  }

  public record ProfilePatch(
      String businessName,
      String email,
      String phone,
      String location,
      boolean rncProvided,
      String rnc) {
    public ProfilePatch {
      if (businessName == null
          && email == null
          && phone == null
          && location == null
          && !rncProvided) {
        throw new IllegalArgumentException("At least one company field is required");
      }
      validateText(businessName, 160);
      validateText(email, 320);
      validateText(location, 255);
      if (phone != null && (phone.isBlank() || !phone.matches("[0-9+() .-]{7,32}"))) {
        throw new IllegalArgumentException("Invalid company phone");
      }
      if (rnc != null && (!rncProvided || !rnc.matches("[0-9]{9}|[0-9]{11}"))) {
        throw new IllegalArgumentException("Invalid company RNC");
      }
    }

    private static void validateText(String value, int maxLength) {
      if (value != null
          && (value.isBlank() || value.length() > maxLength || value.indexOf(0) >= 0)) {
        throw new IllegalArgumentException("Invalid company profile field");
      }
    }
  }

  public Company {
    if (id == null
        || name == null
        || name.isBlank()
        || businessName == null
        || businessName.isBlank()) {
      throw new IllegalArgumentException("A company requires an id and a name");
    }
    if (!"DOP".equals(currency) || !("ACTIVE".equals(status) || "INACTIVE".equals(status))) {
      throw new IllegalArgumentException("Invalid company defaults");
    }
  }
}
