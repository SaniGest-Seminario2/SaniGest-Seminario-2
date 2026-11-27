package com.sanigest.catalog.application.usecase.client;

import static com.sanigest.catalog.application.port.out.integration.CatalogAuthorization.Permission.CATALOG_READ;

import com.sanigest.catalog.application.exception.InvalidClientQueryException;
import com.sanigest.catalog.application.port.out.integration.SalesClientFinancials;
import com.sanigest.catalog.application.port.out.integration.SalesClientFinancials.Statement;
import com.sanigest.catalog.application.port.out.persistence.ClientRepository;
import com.sanigest.catalog.domain.model.Client;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@PreAuthorize("T(com.sanigest.companyaccess.AuthenticatedActor).userId().isPresent()")
public class ClientFinancials {
  private final ClientCompany company;
  private final GetClient getClient;
  private final ClientRepository clients;
  private final SalesClientFinancials sales;

  ClientFinancials(
      ClientCompany company,
      GetClient getClient,
      ClientRepository clients,
      SalesClientFinancials sales) {
    this.company = company;
    this.getClient = getClient;
    this.clients = clients;
    this.sales = sales;
  }

  public Summary summary() {
    var companyId = company.require(CATALOG_READ);
    var counts = clients.counts(companyId);
    var totals = sales.summary(companyId);
    return new Summary(
        counts.total(),
        counts.active(),
        totals.clientsWithPendingBalance(),
        totals.totalPending(),
        totals.asOf());
  }

  public Profile profile(UUID id) {
    var client = getClient.execute(id);
    var totals = sales.totals(client.companyId(), id);
    var profile = client.details().profile();
    var limit = profile == null ? null : profile.creditLimit();
    var available =
        limit == null ? null : limit.subtract(totals.creditExposure()).max(BigDecimal.ZERO);
    return new Profile(
        client,
        totals.balance(),
        available,
        totals.totalInvoiced(),
        totals.paymentsReceived(),
        totals.asOf());
  }

  public Statement statement(UUID id, int page, int size) {
    if (page < 0 || size < 1 || size > 100) {
      throw new InvalidClientQueryException();
    }
    var client = getClient.execute(id);
    return sales.statement(client.companyId(), id, page, size);
  }

  public record Summary(
      long totalClients,
      long activeClients,
      long clientsWithPendingBalance,
      BigDecimal totalPending,
      Instant asOf) {}

  public record Profile(
      Client client,
      BigDecimal balance,
      BigDecimal availableCredit,
      BigDecimal totalInvoiced,
      BigDecimal paymentsReceived,
      Instant asOf) {}
}
