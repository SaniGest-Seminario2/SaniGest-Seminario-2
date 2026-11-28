package com.sanigest.finance.api.controller.cash;

import com.sanigest.companyaccess.application.port.out.CompanyAccess.Scope;
import com.sanigest.finance.api.error.FinancialSourceResponses;
import com.sanigest.finance.api.security.CashAccess;
import com.sanigest.finance.application.dto.cash.CashSummary.Summary;
import com.sanigest.finance.application.usecase.cash.ActiveCashSummary;
import com.sanigest.security.api.request.company.CompanySelector;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ActiveCashSummaryController {
  private final ActiveCashSummary summary;

  private final CashAccess access;

  ActiveCashSummaryController(ActiveCashSummary summary, CashAccess access) {
    this.summary = summary;
    this.access = access;
  }

  @PreAuthorize("isAuthenticated()")
  @GetMapping("/api/v1/cash-sessions/active/summary")
  ResponseEntity<Summary> activeSummary(
      @AuthenticationPrincipal Jwt jwt, @RequestHeader HttpHeaders headers) {
    UUID company = access.require(headers.getOrEmpty(CompanySelector.HEADER), Scope.READ);
    return ResponseEntity.of(
        FinancialSourceResponses.execute(
            () -> summary.find(company, UUID.fromString(jwt.getSubject()), jwt.getTokenValue())));
  }
}
