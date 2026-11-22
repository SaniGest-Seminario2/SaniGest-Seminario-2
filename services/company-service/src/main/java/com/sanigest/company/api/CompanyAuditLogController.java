package com.sanigest.company.api;

import static com.sanigest.company.api.CompanyController.authenticatedUserId;

import com.sanigest.company.application.CurrentAccount;
import com.sanigest.company.application.GetCompanyAuditLogUseCase;
import com.sanigest.company.application.GetCompanyAuditLogUseCase.AuditPage;
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
  private final CurrentAccount accounts;
  private final GetCompanyAuditLogUseCase auditLog;

  CompanyAuditLogController(CurrentAccount accounts, GetCompanyAuditLogUseCase auditLog) {
    this.accounts = accounts;
    this.auditLog = auditLog;
  }

  @GetMapping("/api/v1/companies/me/audit-log")
  ResponseEntity<AuditPage> get(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    var result = auditLog.get(authenticatedUserId(jwt, accounts), from, to, page, size);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
  }
}
