package com.sanigest.sales.api.controller;

import com.sanigest.sales.application.usecase.client.ClientFinancials;
import com.sanigest.sales.application.usecase.client.ClientFinancials.CompanyTotals;
import com.sanigest.sales.application.usecase.client.ClientFinancials.Statement;
import com.sanigest.sales.application.usecase.client.ClientFinancials.Totals;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/client-financials")
class ClientFinancialsController {
  private final ClientFinancials financials;

  ClientFinancialsController(ClientFinancials financials) {
    this.financials = financials;
  }

  @PostMapping("/balances")
  Map<UUID, BigDecimal> balances(
      @RequestParam UUID expectedCompanyId, @RequestBody List<UUID> ids) {
    return financials.balances(expectedCompanyId, ids);
  }

  @GetMapping("/summary")
  CompanyTotals summary(@RequestParam UUID expectedCompanyId) {
    return financials.companyTotals(expectedCompanyId);
  }

  @GetMapping("/{id}/summary")
  Totals totals(@RequestParam UUID expectedCompanyId, @PathVariable UUID id) {
    return financials.totals(expectedCompanyId, id);
  }

  @GetMapping("/{id}/statement")
  Statement statement(
      @RequestParam UUID expectedCompanyId,
      @PathVariable UUID id,
      @RequestParam int page,
      @RequestParam int size) {
    return financials.statement(expectedCompanyId, id, page, size);
  }
}
