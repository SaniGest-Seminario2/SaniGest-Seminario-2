package com.sanigest.finance.api;

import com.sanigest.finance.infrastructure.FinancialProjections;
import com.sanigest.security.CompanySelector;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class FinancialProjectionController {
  private final FinancialProjections projections;

  FinancialProjectionController(FinancialProjections projections) {
    this.projections = projections;
  }

  @GetMapping(
      path = {"/api/v1/dashboard/projection", "/api/v1/reports/projection"},
      produces = MediaType.APPLICATION_JSON_VALUE)
  String projection(@RequestHeader HttpHeaders headers, @AuthenticationPrincipal Jwt jwt) {
    return projections.read(company(headers), jwt.getTokenValue());
  }

  @PostMapping(
      path = "/api/v1/reports/projection/rebuild",
      produces = MediaType.APPLICATION_JSON_VALUE)
  String rebuild(@RequestHeader HttpHeaders headers, @AuthenticationPrincipal Jwt jwt) {
    return projections.rebuild(company(headers), jwt.getTokenValue());
  }

  private static UUID company(HttpHeaders headers) {
    try {
      return CompanySelector.resolve(headers.getOrEmpty(CompanySelector.HEADER), null, true);
    } catch (CompanySelector.InvalidContext invalid) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalid.getMessage());
    }
  }
}
