package com.sanigest.expense.api;

import com.sanigest.companyaccess.CompanyAccess.Scope;
import com.sanigest.expense.application.ExpenseFailure;
import com.sanigest.expense.application.ExpenseQueries;
import com.sanigest.expense.application.ExpenseQueries.Criteria;
import com.sanigest.expense.application.ExpenseQueries.Detail;
import com.sanigest.expense.application.ExpenseQueries.Page;
import com.sanigest.expense.application.ExpenseQueries.Row;
import com.sanigest.expense.application.ExpenseQueries.Status;
import com.sanigest.expense.application.ExpenseQueries.Summary;
import com.sanigest.expense.application.ExpenseRegistration.OperationType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/expenses")
class ExpenseReadController {
  private final ExpenseQueries queries;
  private final ExpenseCompanyAccess companies;

  ExpenseReadController(ExpenseQueries queries, ExpenseCompanyAccess companies) {
    this.queries = queries;
    this.companies = companies;
  }

  @GetMapping
  Page page(
      HttpServletRequest http,
      @Valid @ModelAttribute Filters filters,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return queries.page(companies.require(http, Scope.READ), filters.criteria(), page, size);
  }

  @GetMapping("/all")
  List<Row> all(HttpServletRequest http, @Valid @ModelAttribute Filters filters) {
    return queries.all(companies.require(http, Scope.READ), filters.criteria());
  }

  @GetMapping("/summary")
  Summary summary(HttpServletRequest http, @Valid @ModelAttribute Filters filters) {
    return queries.summary(companies.require(http, Scope.READ), filters.criteria());
  }

  @GetMapping("/{id}")
  Detail detail(HttpServletRequest http, @PathVariable UUID id) {
    return queries
        .detail(companies.require(http, Scope.READ), id)
        .orElseThrow(() -> new ExpenseFailure(ExpenseFailure.Kind.NOT_FOUND, "EXPENSE_NOT_FOUND"));
  }

  record Filters(
      @Size(max = 200) String search,
      String operationType,
      String status,
      @DateTimeFormat(iso = ISO.DATE) LocalDate from,
      @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
    Criteria criteria() {
      return new Criteria(
          search == null ? "" : search,
          filter(OperationType.class, operationType),
          filter(Status.class, status),
          from,
          to);
    }

    private static <E extends Enum<E>> E filter(Class<E> type, String value) {
      if (value == null || value.isBlank() || value.equals("ALL")) {
        return null;
      }
      try {
        return Enum.valueOf(type, value);
      } catch (IllegalArgumentException invalid) {
        throw ExpenseFailure.invalid("EXPENSE_INVALID_FILTER");
      }
    }
  }
}
