package com.sanigest.sales.application.usecase.client;

import com.sanigest.sales.application.exception.sale.InvalidSaleRequest;
import com.sanigest.sales.application.exception.security.SalesAccessForbidden;
import com.sanigest.sales.application.port.out.integration.company.SalesAuthorization;
import com.sanigest.sales.application.port.out.integration.company.SalesAuthorization.Permission;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class ClientFinancials {
  private final SalesAuthorization authorization;
  private final Ledger ledger;

  public ClientFinancials(SalesAuthorization authorization, Ledger ledger) {
    this.authorization = authorization;
    this.ledger = ledger;
  }

  public Map<UUID, BigDecimal> balances(UUID expectedCompany, List<UUID> clientIds) {
    var company = authorizedCompany(expectedCompany);
    if (clientIds == null
        || clientIds.isEmpty()
        || clientIds.size() > 500
        || clientIds.stream().anyMatch(Objects::isNull)) {
      throw new InvalidSaleRequest();
    }
    return ledger.balances(company, clientIds);
  }

  public CompanyTotals companyTotals(UUID expectedCompany) {
    return ledger.companyTotals(authorizedCompany(expectedCompany));
  }

  public Totals totals(UUID expectedCompany, UUID clientId) {
    return ledger.totals(authorizedCompany(expectedCompany), clientId);
  }

  public Statement statement(UUID expectedCompany, UUID clientId, int page, int size) {
    var company = authorizedCompany(expectedCompany);
    if (page < 0 || size < 1 || size > 100) {
      throw new InvalidSaleRequest();
    }
    return ledger.statement(company, clientId, page, size);
  }

  private UUID authorizedCompany(UUID expectedCompany) {
    var company = authorization.requireMembership(Permission.SALES_READ);
    if (!company.equals(expectedCompany)) {
      throw new SalesAccessForbidden();
    }
    return company;
  }

  public interface Ledger {
    Map<UUID, BigDecimal> balances(UUID company, List<UUID> clientIds);

    CompanyTotals companyTotals(UUID company);

    Totals totals(UUID company, UUID clientId);

    Statement statement(UUID company, UUID clientId, int page, int size);
  }

  public record CompanyTotals(
      long clientsWithPendingBalance, BigDecimal totalPending, Instant asOf) {}

  public record Totals(
      BigDecimal balance,
      BigDecimal creditExposure,
      BigDecimal totalInvoiced,
      BigDecimal paymentsReceived,
      Instant asOf) {}

  public record Invoice(UUID id, LocalDate date, BigDecimal amount, String status) {}

  public record Payment(UUID id, UUID invoiceId, Instant date, BigDecimal amount) {}

  public record Statement(
      List<Invoice> invoices,
      List<Payment> payments,
      int page,
      int size,
      boolean hasMoreInvoices,
      boolean hasMorePayments,
      Instant asOf) {}
}
