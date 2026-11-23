package com.sanigest.sales.application.usecase.invoice;

import com.sanigest.sales.application.exception.InvoiceCompanyUnavailable;
import com.sanigest.sales.application.exception.InvoiceConflict;
import com.sanigest.sales.application.port.out.integration.InvoiceCompanyAccess;
import com.sanigest.sales.application.port.out.persistence.InvoiceRepository;
import com.sanigest.sales.application.port.out.persistence.InvoiceRepository.Request;
import com.sanigest.sales.domain.model.InternalInvoice;
import com.sanigest.sales.domain.model.InternalInvoice.CompanySnapshot;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

public final class IssueInvoice {
  private final InvoiceCompanyAccess companyAccess;
  private final Supplier<UUID> actor;
  private final InvoiceRepository invoices;
  private final Clock clock;

  public IssueInvoice(
      InvoiceCompanyAccess companyAccess,
      Supplier<UUID> actor,
      InvoiceRepository invoices,
      Clock clock) {
    this.companyAccess = companyAccess;
    this.actor = actor;
    this.invoices = invoices;
    this.clock = clock;
  }

  public String execute(UUID saleId, String key, Function<InternalInvoice, String> response) {
    var company = companyAccess.requireInvoiceCompany();
    var issuedBy = actor.get();
    var at = clock.instant().truncatedTo(ChronoUnit.MICROS);
    var request =
        new Request(
            company.id(),
            issuedBy,
            saleId,
            key,
            at.atZone(InternalInvoice.BUSINESS_ZONE).getYear());
    return invoices.issue(
        request,
        (sale, number) -> {
          if (!InternalInvoice.isEligible(sale)) {
            throw new InvoiceConflict();
          }
          CompanySnapshot snapshot;
          try {
            snapshot = new CompanySnapshot(company.id(), company.name());
          } catch (IllegalArgumentException invalid) {
            throw new InvoiceCompanyUnavailable();
          }
          return InternalInvoice.issue(sale, snapshot, number.get(), at, issuedBy);
        },
        response);
  }
}
