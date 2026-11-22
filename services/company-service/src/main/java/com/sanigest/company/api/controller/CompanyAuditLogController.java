package com.sanigest.company.api.controller;

import static com.sanigest.company.api.controller.CompanyActor.authenticatedUserId;

import com.sanigest.company.api.dto.CompanyAuditResponse;
import com.sanigest.company.application.usecase.access.RequireRegularAccount;
import com.sanigest.company.application.usecase.audit.GetCompanyAuditLogUseCase;
import java.time.Instant;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

  @GetMapping("/api/v1/companies/me/audit-log")
  ResponseEntity<CompanyAuditResponse> get(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    var result = auditLog.get(authenticatedUserId(jwt, regularAccount), from, to, page, size);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(CompanyAuditResponse.from(result));
  }
}
