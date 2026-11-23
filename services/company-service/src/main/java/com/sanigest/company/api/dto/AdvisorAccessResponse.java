package com.sanigest.company.api.dto;

import com.sanigest.company.domain.model.AdvisorAccess;
import java.util.List;
import java.util.UUID;

public record AdvisorAccessResponse(UUID advisorId, long revision, List<Permission> permissions) {
  public static AdvisorAccessResponse from(AdvisorAccess access) {
    return new AdvisorAccessResponse(
        access.advisorId(),
        access.revision(),
        access.permissions().stream()
            .map(
                permission -> new Permission(permission.module().name(), permission.scope().name()))
            .toList());
  }

  public record Permission(String module, String scope) {}
}
