package com.sanigest.company.api.controller.audit;

import static com.sanigest.company.api.routing.CompanyRequestSelection.resolve;
import static com.sanigest.company.api.security.CompanyActor.authenticatedUserId;

import com.sanigest.company.api.dto.audit.CompanyAuditResponse;
import com.sanigest.company.application.usecase.access.RequireRegularAccount;
import com.sanigest.company.application.usecase.audit.GetCompanyAuditLogUseCase;
import com.sanigest.security.api.dto.audit.CompanyAuditParameters;
import jakarta.servlet.http.HttpServletRequest;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CompanyAuditLogController {
  private final RequireRegularAccount regularAccount;
  private final GetCompanyAuditLogUseCase auditLog;

  CompanyAuditLogController(
      RequireRegularAccount regularAccount, GetCompanyAuditLogUseCase auditLog) {
    this.regularAccount = regularAccount;
    this.auditLog = auditLog;
  }

  @GetMapping({"/api/v1/companies/me/audit-log", "/api/v1/companies/{companyId}/audit-log"})
  ResponseEntity<CompanyAuditResponse> get(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable(required = false) String companyId,
      HttpServletRequest http,
      @ParameterObject @ModelAttribute CompanyAuditParameters parameters) {
    var result =
        auditLog.get(
            authenticatedUserId(jwt, regularAccount),
            resolve(http, companyId),
            parameters.filters());
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(CompanyAuditResponse.from(result));
  }
}
