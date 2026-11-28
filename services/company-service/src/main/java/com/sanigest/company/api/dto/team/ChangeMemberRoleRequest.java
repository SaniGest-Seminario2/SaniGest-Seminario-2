package com.sanigest.company.api.dto.team;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.sanigest.company.domain.enums.team.CompanyRole;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

public class ChangeMemberRoleRequest {
  @NotNull private CompanyRole role;

  public CompanyRole role() {
    return role;
  }

  @JsonSetter("role")
  void setRole(JsonNode value) {
    if (value == null || !value.isString()) {
      throw new IllegalArgumentException("A role name is required");
    }
    role = CompanyRole.valueOf(value.stringValue());
  }

  @JsonAnySetter
  void rejectUnknownField(String name, JsonNode value) {
    throw new IllegalArgumentException("Unsupported membership field");
  }
}
