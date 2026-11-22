package com.sanigest.company.api.controller;

import com.sanigest.company.api.dto.AuthorizedAdvisorCompanyResponse;
import com.sanigest.company.application.usecase.advisor.ListAuthorizedAdvisorCompaniesUseCase;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AdvisorCompaniesController {
  static final String PATH = "/api/v1/advisor/companies";
  private final ListAuthorizedAdvisorCompaniesUseCase companies;

  AdvisorCompaniesController(ListAuthorizedAdvisorCompaniesUseCase companies) {
    this.companies = companies;
  }

  @GetMapping(PATH)
  ResponseEntity<List<AuthorizedAdvisorCompanyResponse>> list(@AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            companies.list(UUID.fromString(jwt.getSubject())).stream()
                .map(AuthorizedAdvisorCompanyResponse::from)
                .toList());
  }
}
