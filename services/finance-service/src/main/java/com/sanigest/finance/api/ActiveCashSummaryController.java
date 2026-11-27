package com.sanigest.finance.api;

import com.sanigest.finance.application.ActiveCashSummary;
import com.sanigest.finance.application.ActiveCashSummary.Summary;
import com.sanigest.security.CompanySelector;
import com.sanigest.security.CompanySelector.InvalidContext;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class ActiveCashSummaryController {
  private final ActiveCashSummary summary;

  ActiveCashSummaryController(ActiveCashSummary summary) {
    this.summary = summary;
  }

  @GetMapping("/api/v1/cash-sessions/active/summary")
  ResponseEntity<Summary> activeSummary(
      @AuthenticationPrincipal Jwt jwt, @RequestHeader HttpHeaders headers) {
    UUID company;
    try {
      company = CompanySelector.resolve(headers.getOrEmpty(CompanySelector.HEADER), null, true);
    } catch (InvalidContext invalid) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalid.getMessage());
    }
    return ResponseEntity.of(
        summary.find(company, UUID.fromString(jwt.getSubject()), jwt.getTokenValue()));
  }
}
