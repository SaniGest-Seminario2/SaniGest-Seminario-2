package com.sanigest.company.application.dto;

import com.sanigest.company.domain.model.AdvisorPermission;
import java.util.List;
import java.util.UUID;

/** Minimal company projection for an advisor's selector. */
public record AuthorizedAdvisorCompany(
    UUID companyId, String companyName, List<AdvisorPermission> permissions) {
  public AuthorizedAdvisorCompany {
    permissions = List.copyOf(permissions);
  }
}
