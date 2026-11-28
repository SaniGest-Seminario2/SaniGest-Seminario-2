package com.sanigest.expense.api.controller;

import com.sanigest.companyaccess.CompanyAccess.Scope;
import com.sanigest.expense.api.security.ExpenseCompanyAccess;
import com.sanigest.expense.application.port.in.FinancialSnapshot;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
class FinancialSnapshotController {
  private final FinancialSnapshot snapshots;
  private final ExpenseCompanyAccess companies;

  FinancialSnapshotController(FinancialSnapshot snapshots, ExpenseCompanyAccess companies) {
    this.snapshots = snapshots;
    this.companies = companies;
  }

  @RequestMapping(
      path = "/internal/financial-snapshot/v1",
      method = {RequestMethod.GET, RequestMethod.HEAD},
      produces = MediaType.APPLICATION_JSON_VALUE)
  String snapshot(HttpServletRequest request) {
    var company = companies.require(request, Scope.READ);
    if (HttpMethod.HEAD.matches(request.getMethod())) {
      return null;
    }
    return snapshots.read(company);
  }
}
