package com.sanigest.expense.api.controller.payable;

import com.sanigest.companyaccess.application.port.out.CompanyAccess.Scope;
import com.sanigest.expense.api.security.ExpenseCompanyAccess;
import com.sanigest.expense.application.port.in.payable.PayableQueries;
import com.sanigest.expense.application.port.in.payable.PayableQueries.Criteria;
import com.sanigest.expense.application.port.in.payable.PayableQueries.Filter;
import com.sanigest.expense.application.port.in.payable.PayableQueries.Page;
import com.sanigest.expense.application.port.in.payable.PayableQueries.Payable;
import com.sanigest.expense.application.port.in.payable.PayableQueries.SupplierBalance;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payables")
class PayableController {
  private final PayableQueries queries;
  private final ExpenseCompanyAccess companies;

  PayableController(PayableQueries queries, ExpenseCompanyAccess companies) {
    this.queries = queries;
    this.companies = companies;
  }

  @GetMapping
  Page page(
      HttpServletRequest http,
      @RequestParam(defaultValue = "") @Size(max = 200) String search,
      @RequestParam(defaultValue = "ALL") Filter status,
      @RequestParam(required = false) UUID supplierId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return queries.page(
        companies.require(http, Scope.READ), new Criteria(search, status, supplierId), page, size);
  }

  @GetMapping("/all")
  List<Payable> all(
      HttpServletRequest http,
      @RequestParam(defaultValue = "") @Size(max = 200) String search,
      @RequestParam(defaultValue = "ALL") Filter status,
      @RequestParam(required = false) UUID supplierId) {
    return queries.all(
        companies.require(http, Scope.READ), new Criteria(search, status, supplierId));
  }

  @GetMapping("/supplier-balances")
  List<SupplierBalance> supplierBalances(
      HttpServletRequest http,
      @RequestParam @Size(min = 1, max = 100) List<@NotNull UUID> supplierIds) {
    return queries.supplierBalances(companies.require(http, Scope.READ), supplierIds);
  }
}
