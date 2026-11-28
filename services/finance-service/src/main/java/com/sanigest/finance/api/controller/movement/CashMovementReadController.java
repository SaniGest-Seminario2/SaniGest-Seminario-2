package com.sanigest.finance.api.controller.movement;

import com.sanigest.companyaccess.application.port.out.CompanyAccess.Scope;
import com.sanigest.finance.api.security.CashAccess;
import com.sanigest.finance.application.dto.movement.ManualMovement.Category;
import com.sanigest.finance.application.dto.movement.ManualMovement.Method;
import com.sanigest.finance.application.dto.movement.ManualMovement.Type;
import com.sanigest.finance.application.port.in.movement.CashMovementQueries;
import com.sanigest.finance.application.port.in.movement.CashMovementQueries.Criteria;
import com.sanigest.finance.application.port.in.movement.CashMovementQueries.Origin;
import com.sanigest.finance.application.port.in.movement.CashMovementQueries.Page;
import com.sanigest.finance.application.port.in.movement.CashMovementQueries.Row;
import com.sanigest.finance.application.port.in.movement.CashMovementQueries.Summary;
import com.sanigest.security.api.request.company.CompanySelector;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/cash-movements")
@PreAuthorize("isAuthenticated()")
class CashMovementReadController {
  private final CashMovementQueries movements;
  private final CashAccess access;

  CashMovementReadController(CashMovementQueries movements, CashAccess access) {
    this.movements = movements;
    this.access = access;
  }

  @GetMapping
  Page page(
      HttpServletRequest http,
      @Valid @ParameterObject @ModelAttribute Filters filters,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return movements.page(company(http), filters.criteria(), page, size);
  }

  @GetMapping("/all")
  @ApiResponse(responseCode = "422", description = "More than 1000 matching movements")
  List<Row> all(HttpServletRequest http, @Valid @ParameterObject @ModelAttribute Filters filters) {
    List<Row> rows = movements.all(company(http), filters.criteria());
    if (rows.size() > CashMovementQueries.ALL_LIMIT) {
      throw new ResponseStatusException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "More than 1000 movements match. Use pagination or narrow the filters.");
    }
    return rows;
  }

  @GetMapping("/summary")
  @Operation(summary = "Summarize confirmed DOP movements using inclusive Santo Domingo dates")
  Summary summary(
      HttpServletRequest http, @Valid @ParameterObject @ModelAttribute Filters filters) {
    return movements.summary(company(http), filters.criteria());
  }

  private UUID company(HttpServletRequest http) {
    return access.require(Collections.list(http.getHeaders(CompanySelector.HEADER)), Scope.READ);
  }

  record Filters(
      @Size(max = 200) String search,
      @Schema(allowableValues = {"ALL", "INCOME", "EXPENSE", "INTERNAL_TRANSFER"}) String type,
      @Schema(
              allowableValues = {
                "ALL",
                "CASH_SESSION",
                "RECEIVABLE_PAYMENT",
                "PAYABLE_PAYMENT",
                "MANUAL"
              })
          String origin,
      @Schema(allowableValues = {"ALL", "CASH", "CARD", "TRANSFER", "CHECK"}) String method,
      @Schema(
              allowableValues = {
                "ALL",
                "SALES",
                "SUPPLIES",
                "PARTNER_CONTRIBUTION",
                "LOAN_RECEIVED",
                "BANK_INTEREST",
                "OTHER_INCOME"
              })
          String category,
      @DateTimeFormat(iso = ISO.DATE) LocalDate from,
      @DateTimeFormat(iso = ISO.DATE) LocalDate to) {

    Criteria criteria() {
      if (from != null && to != null && from.isAfter(to)) {
        throw invalid("from must be on or before to");
      }
      validateDate(from);
      validateDate(to);
      String categoryFilter =
          switch (category == null ? "" : category) {
            case "SALES", "SUPPLIES" -> category;
            default -> {
              Category manual = filter(Category.class, category);
              yield manual == null ? null : manual.name();
            }
          };
      if (search != null && search.indexOf(0) >= 0) {
        throw invalid("Invalid search");
      }
      return new Criteria(
          search == null ? "" : search.strip(),
          filter(Type.class, type),
          filter(Origin.class, origin),
          filter(Method.class, method),
          categoryFilter,
          from,
          to);
    }

    private static void validateDate(LocalDate date) {
      if (date != null && (date.getYear() < 1 || date.getYear() > 9999)) {
        throw invalid("dates must have a year between 1 and 9999");
      }
    }

    private static <E extends Enum<E>> E filter(Class<E> type, String value) {
      if (value == null || value.isBlank() || value.equals("ALL")) {
        return null;
      }
      try {
        return Enum.valueOf(type, value);
      } catch (IllegalArgumentException invalid) {
        throw invalid(
            "Invalid " + type.getSimpleName().toLowerCase(java.util.Locale.ROOT) + " filter");
      }
    }

    private static ResponseStatusException invalid(String message) {
      return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
  }
}
