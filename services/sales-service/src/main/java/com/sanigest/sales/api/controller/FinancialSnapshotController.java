package com.sanigest.sales.api.controller;

import com.sanigest.sales.application.FinancialSnapshot;
import com.sanigest.sales.application.usecase.sale.ListSales;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
class FinancialSnapshotController {
  private final FinancialSnapshot snapshots;
  private final ListSales sales;

  FinancialSnapshotController(FinancialSnapshot snapshots, ListSales sales) {
    this.snapshots = snapshots;
    this.sales = sales;
  }

  @RequestMapping(
      path = "/internal/financial-snapshot/v1",
      method = {RequestMethod.GET, RequestMethod.HEAD},
      produces = MediaType.APPLICATION_JSON_VALUE)
  String snapshot(HttpServletRequest request) {
    var company = sales.requireReadCompany();
    if (HttpMethod.HEAD.matches(request.getMethod())) {
      return null;
    }
    return snapshots.read(company);
  }
}
