package com.sanigest.company.domain.model;

import java.util.List;
import java.util.UUID;

public record AdvisorAccess(UUID advisorId, long revision, List<AdvisorPermission> permissions) {
  public AdvisorAccess {
    permissions = List.copyOf(permissions);
  }
}
