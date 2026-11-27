package com.sanigest.sales.application.usecase.invoice;

import static com.sanigest.sales.application.port.out.integration.SalesAuthorization.Permission.SALES_CANCEL;
import static com.sanigest.sales.application.port.out.integration.SalesAuthorization.Permission.SALES_READ;

import com.sanigest.sales.application.dto.InvoicePage;
import com.sanigest.sales.application.exception.InvalidSaleQuery;
import com.sanigest.sales.application.exception.InvalidSaleRequest;
import com.sanigest.sales.application.exception.InvoiceListLimitExceeded;
import com.sanigest.sales.application.exception.InvoiceNotFound;
import com.sanigest.sales.application.port.out.integration.SalesAuthorization;
import com.sanigest.sales.application.port.out.persistence.InvoiceDocuments;
import com.sanigest.sales.application.port.out.persistence.InvoiceDocuments.Search;
import com.sanigest.sales.domain.model.SaleCancellation;
import java.time.Clock;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public final class ManageInvoices {
  public static final int MAX_UNPAGED_RESULTS = 1_000;

  private static final Set<String> STATUSES =
      Set.of("", "ALL", "ISSUED", "CANCELLED", "PARTIALLY_PAID", "PAID");

  private final SalesAuthorization authorization;
  private final InvoiceDocuments documents;
  private final Supplier<UUID> actor;
  private final Clock clock;

  public ManageInvoices(
      SalesAuthorization authorization,
      InvoiceDocuments documents,
      Supplier<UUID> actor,
      Clock clock) {
    this.authorization = authorization;
    this.documents = documents;
    this.actor = actor;
    this.clock = clock;
  }

  public InvoicePage list(
      String search, String status, boolean collectible, int page, Integer size) {
    var company = authorization.requireMembership(SALES_READ);
    String term = search == null ? "" : search.strip();
    String state = status == null ? "" : status.strip().toUpperCase(Locale.ROOT);
    if (term.length() > 250
        || term.indexOf(0) >= 0
        || page < 0
        || (size != null && (size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE))
        || !STATUSES.contains(state)) {
      throw new InvalidSaleQuery();
    }
    String pattern = "%" + term.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    if (size != null) {
      return documents.list(
          company, new Search(pattern, state, collectible, page * size, size, true));
    }
    var result =
        documents.list(
            company, new Search(pattern, state, collectible, 0, MAX_UNPAGED_RESULTS + 1, false));
    if (result.content().size() > MAX_UNPAGED_RESULTS) {
      throw new InvoiceListLimitExceeded();
    }
    return result;
  }

  public String find(UUID invoice) {
    return documents
        .find(authorization.requireMembership(SALES_READ), invoice)
        .orElseThrow(InvoiceNotFound::new);
  }

  public String cancel(UUID invoice, String reason) {
    var company = authorization.requireMembership(SALES_CANCEL);
    SaleCancellation cancellation;
    try {
      cancellation = new SaleCancellation(actor.get(), clock.instant(), reason);
    } catch (IllegalArgumentException invalid) {
      throw new InvalidSaleRequest();
    }
    return documents.cancel(company, invoice, cancellation).orElseThrow(InvoiceNotFound::new);
  }
}
