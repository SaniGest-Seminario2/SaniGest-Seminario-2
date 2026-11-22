package com.sanigest.company.api.dto;

import com.sanigest.company.application.port.out.persistence.AuthorizedAdvisorCompany;
import java.util.List;
import java.util.UUID;

public record AuthorizedAdvisorCompanyResponse(
    UUID companyId, String companyName, List<Permission> permissions) {
  public static AuthorizedAdvisorCompanyResponse from(AuthorizedAdvisorCompany company) {
    return new AuthorizedAdvisorCompanyResponse(
        company.companyId(),
        company.companyName(),
        company.permissions().stream()
            .map(
                permission -> new Permission(permission.module().name(), permission.scope().name()))
            .toList());
  }

  public record Permission(String module, String scope) {}
}
