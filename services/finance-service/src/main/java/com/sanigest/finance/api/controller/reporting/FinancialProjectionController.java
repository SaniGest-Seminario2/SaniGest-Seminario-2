package com.sanigest.finance.api.controller.reporting;

import com.sanigest.finance.api.error.FinancialSourceResponses;
import com.sanigest.finance.application.usecase.reporting.FinancialProjections;
import com.sanigest.security.api.request.company.CompanySelector;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
  @ApiResponse(
      responseCode = "200",
      description = "Financial projection",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = ProjectionResponse.class)))
  String projection(@RequestHeader HttpHeaders headers, @AuthenticationPrincipal Jwt jwt) {
    return FinancialSourceResponses.execute(
        () -> projections.read(company(headers), jwt.getTokenValue()));
  }

  @PostMapping(
      path = "/api/v1/reports/projection/rebuild",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ApiResponse(
      responseCode = "200",
      description = "Financial projection",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = ProjectionResponse.class)))
  String rebuild(@RequestHeader HttpHeaders headers, @AuthenticationPrincipal Jwt jwt) {
    return FinancialSourceResponses.execute(
        () -> projections.rebuild(company(headers), jwt.getTokenValue()));
  }

  /** Shape of the JSON projection assembled in PostgreSQL. */
  interface ProjectionResponse {
    String getCurrency();

    Instant getGeneratedAt();

    @Schema(
        types = {"string", "null"},
        format = "date-time")
    Instant getDataAsOf();

    boolean getPartial();

    List<String> getMissingSources();

    @Schema(
        types = {"string", "null"},
        format = "date-time")
    Instant getSalesAsOf();

    @Schema(
        types = {"string", "null"},
        format = "date-time")
    Instant getExpenseAsOf();

    @Schema(
        types = {"string", "null"},
        format = "date-time")
    Instant getCashLatestEventAt();

    @Schema(types = {"number", "null"})
    BigDecimal getReceivables();

    @Schema(types = {"number", "null"})
    BigDecimal getPayables();

    @Schema(types = {"array", "null"})
    List<DocumentSeries> getSalesSeries();

    @Schema(types = {"array", "null"})
    List<DocumentSeries> getExpenseSeries();

    @Schema(types = {"array", "null"})
    List<DocumentTotal> getDocumentTotals();

    @Schema(types = {"array", "null"})
    List<DocumentActivity> getRecentInvoices();

    List<CashSeries> getCashSeries();

    CashTotals getCashTotals();

    List<Activity> getActivity();
  }

  interface DocumentSeries {
    LocalDate getMonth();

    String getKind();

    BigDecimal getAmount();

    long getCount();
  }

  interface DocumentTotal {
    String getKind();

    BigDecimal getAmount();

    long getCount();
  }

  interface CashSeries {
    LocalDate getMonth();

    BigDecimal getInflow();

    BigDecimal getOutflow();

    BigDecimal getNet();
  }

  interface CashTotals {
    BigDecimal getInflow();

    BigDecimal getOutflow();

    BigDecimal getNet();
  }

  @Schema(requiredProperties = {"id", "date", "kind", "total", "status"})
  interface DocumentActivity {
    UUID getId();

    LocalDate getDate();

    String getKind();

    BigDecimal getTotal();

    String getStatus();
  }

  @Schema(requiredProperties = {"id", "date", "kind", "origin", "occurredAt", "amount"})
  interface CashActivity {
    UUID getId();

    LocalDate getDate();

    String getKind();

    String getOrigin();

    Instant getOccurredAt();

    BigDecimal getAmount();
  }

  @Schema(oneOf = {DocumentActivity.class, CashActivity.class})
  interface Activity {}

  private static UUID company(HttpHeaders headers) {
    try {
      return CompanySelector.resolve(headers.getOrEmpty(CompanySelector.HEADER), null, true);
    } catch (CompanySelector.InvalidContext invalid) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalid.getMessage());
    }
  }
}
