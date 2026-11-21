package com.sanigest.company.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.sanigest.company.domain.model.Company.ProfilePatch;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

/** Omitted fields are preserved. Only RNC accepts null, which clears it. */
final class CompanyPatchRequest {
  @Size(max = 160) private String businessName;

  @Email @Size(max = 320) private String email;

  @Pattern(regexp = "[0-9+() .-]{7,32}")
  private String phone;

  @Size(max = 255) private String location;

  @Pattern(regexp = "[0-9]{9}|[0-9]{11}") private String rnc;

  private boolean rncProvided;

  @JsonSetter("businessName")
  void setBusinessName(JsonNode value) {
    businessName = requiredText(value);
  }

  @JsonSetter("email")
  void setEmail(JsonNode value) {
    email = requiredText(value);
  }

  @JsonSetter("phone")
  void setPhone(JsonNode value) {
    phone = requiredText(value);
  }

  @JsonSetter("location")
  void setLocation(JsonNode value) {
    location = requiredText(value);
  }

  @JsonSetter("rnc")
  void setRnc(JsonNode value) {
    rncProvided = true;
    rnc = value == null || value.isNull() ? null : requiredText(value);
  }

  @JsonAnySetter
  void rejectUnknownField(String name, JsonNode value) {
    throw new IllegalArgumentException("Unsupported company profile field");
  }

  ProfilePatch toPatch() {
    return new ProfilePatch(businessName, email, phone, location, rncProvided, rnc);
  }

  private static String requiredText(JsonNode value) {
    if (value == null || !value.isString() || value.stringValue().isBlank()) {
      throw new IllegalArgumentException("A non-blank string is required");
    }
    return value.stringValue();
  }
}
