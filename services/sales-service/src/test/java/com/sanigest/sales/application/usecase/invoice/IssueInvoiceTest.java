package com.sanigest.sales.application.usecase.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sanigest.sales.application.exception.sale.SaleNotFound;
import com.sanigest.sales.application.exception.security.SalesAccessForbidden;
import com.sanigest.sales.application.exception.security.SalesAuthorizationUnavailable;
import com.sanigest.sales.application.port.out.integration.company.InvoiceCompanyAccess;
import com.sanigest.sales.application.port.out.integration.company.InvoiceCompanyAccess.CompanyContext;
import com.sanigest.sales.application.port.out.persistence.invoice.InvoiceRepository;
import com.sanigest.sales.application.port.out.persistence.invoice.InvoiceRepository.Request;
import com.sanigest.sales.domain.model.invoice.InternalInvoice;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IssueInvoiceTest {
  private final UUID companyId = UUID.randomUUID();
  private final UUID actor = UUID.randomUUID();
  private final UUID sale = UUID.randomUUID();
  private final InvoiceCompanyAccess company = mock(InvoiceCompanyAccess.class);
  private final InvoiceRepository invoices = mock(InvoiceRepository.class);
  private final IssueInvoice issue =
      new IssueInvoice(
          company,
          () -> actor,
          invoices,
          Clock.fixed(Instant.parse("2027-01-01T03:59:59Z"), ZoneOffset.UTC));
  private final Request request = new Request(companyId, actor, sale, "key", 2026);

  @Test
  void returnsTheCommittedResponseEvenIfTheCurrentNameIsUnavailable() {
    when(company.requireInvoiceCompany()).thenReturn(new CompanyContext(companyId, null));
    when(invoices.issue(eq(request), any(), any())).thenReturn("original");
    assertThat(issue.execute(sale, "key", InternalInvoice::number)).isEqualTo("original");
  }

  @Test
  void preservesScopedNotFoundFromTheRepository() {
    when(company.requireInvoiceCompany()).thenReturn(new CompanyContext(companyId, "Empresa"));
    when(invoices.issue(eq(request), any(), any())).thenThrow(new SaleNotFound());
    assertThatThrownBy(() -> issue.execute(sale, "key", InternalInvoice::number))
        .isInstanceOf(SaleNotFound.class);
  }

  @Test
  void stopsBeforePersistenceWhenAuthorizationFails() {
    when(company.requireInvoiceCompany())
        .thenThrow(new SalesAccessForbidden())
        .thenThrow(new SalesAuthorizationUnavailable());
    assertThatThrownBy(() -> issue.execute(sale, "key", InternalInvoice::number))
        .isInstanceOf(SalesAccessForbidden.class);
    assertThatThrownBy(() -> issue.execute(sale, "key", InternalInvoice::number))
        .isInstanceOf(SalesAuthorizationUnavailable.class);
    verifyNoInteractions(invoices);
  }
}
