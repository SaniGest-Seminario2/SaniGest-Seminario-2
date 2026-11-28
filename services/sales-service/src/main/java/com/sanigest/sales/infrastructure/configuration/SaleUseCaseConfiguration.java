package com.sanigest.sales.infrastructure.configuration;

import com.sanigest.companyaccess.security.authentication.AuthenticatedActor;
import com.sanigest.sales.application.exception.payment.PaymentAdmissionUnavailable;
import com.sanigest.sales.application.exception.security.SalesAccessForbidden;
import com.sanigest.sales.application.port.out.integration.catalog.CatalogClient;
import com.sanigest.sales.application.port.out.integration.catalog.InvoiceStock;
import com.sanigest.sales.application.port.out.integration.company.InvoiceCompanyAccess;
import com.sanigest.sales.application.port.out.integration.company.SalesAuthorization;
import com.sanigest.sales.application.port.out.integration.finance.ActiveCashShifts;
import com.sanigest.sales.application.port.out.integration.finance.PaymentAdmission;
import com.sanigest.sales.application.port.out.persistence.invoice.InvoiceDocuments;
import com.sanigest.sales.application.port.out.persistence.invoice.InvoiceRepository;
import com.sanigest.sales.application.port.out.persistence.payment.InvoicePayments;
import com.sanigest.sales.application.port.out.persistence.receivable.ReceivableRepository;
import com.sanigest.sales.application.port.out.persistence.sale.SaleIdempotencyRepository;
import com.sanigest.sales.application.port.out.persistence.sale.SaleQueryRepository;
import com.sanigest.sales.application.port.out.persistence.sale.SaleRepository;
import com.sanigest.sales.application.usecase.client.ClientFinancials;
import com.sanigest.sales.application.usecase.invoice.IssueInvoice;
import com.sanigest.sales.application.usecase.invoice.ManageInvoices;
import com.sanigest.sales.application.usecase.payment.ListActiveShiftPayments;
import com.sanigest.sales.application.usecase.payment.RegisterInvoicePayment;
import com.sanigest.sales.application.usecase.receivable.GetReceivable;
import com.sanigest.sales.application.usecase.receivable.ListReceivables;
import com.sanigest.sales.application.usecase.sale.CancelSale;
import com.sanigest.sales.application.usecase.sale.CreateSale;
import com.sanigest.sales.application.usecase.sale.CreateSaleIdempotently;
import com.sanigest.sales.application.usecase.sale.GetSaleDetail;
import com.sanigest.sales.application.usecase.sale.ListSales;
import java.time.Clock;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class SaleUseCaseConfiguration {
  @Bean
  ClientFinancials clientFinancials(
      SalesAuthorization authorization, ClientFinancials.Ledger ledger) {
    return new ClientFinancials(authorization, ledger);
  }

  @Bean
  ListReceivables listReceivables(
      SalesAuthorization authorization,
      ReceivableRepository receivables,
      @Value("${sanigest.sales.max-page-size:100}") int maxPageSize) {
    return new ListReceivables(authorization, receivables, maxPageSize);
  }

  @Bean
  GetReceivable getReceivable(SalesAuthorization authorization, ReceivableRepository receivables) {
    return new GetReceivable(authorization, receivables);
  }

  @Bean
  GetSaleDetail getSaleDetail(SalesAuthorization authorization, SaleQueryRepository query) {
    return new GetSaleDetail(authorization, query);
  }

  @Bean
  CancelSale cancelSale(SalesAuthorization authorization, SaleRepository sales) {
    return new CancelSale(
        authorization, SaleUseCaseConfiguration::actorId, sales, Clock.systemUTC());
  }

  @Bean
  IssueInvoice issueInvoice(InvoiceCompanyAccess companyAccess, InvoiceRepository invoices) {
    return new IssueInvoice(
        companyAccess, SaleUseCaseConfiguration::actorId, invoices, Clock.systemUTC());
  }

  @Bean
  ManageInvoices manageInvoices(SalesAuthorization authorization, InvoiceDocuments documents) {
    return new ManageInvoices(
        authorization, documents, SaleUseCaseConfiguration::actorId, Clock.systemUTC());
  }

  @Bean
  RegisterInvoicePayment registerInvoicePayment(
      SalesAuthorization authorization,
      InvoicePayments payments,
      ObjectProvider<PaymentAdmission> admissions) {
    return new RegisterInvoicePayment(
        authorization,
        SaleUseCaseConfiguration::actorId,
        payments,
        (company, operator, payment, request) -> {
          var authority = admissions.getIfAvailable();
          if (authority == null) {
            throw new PaymentAdmissionUnavailable();
          }
          return authority.admit(company, operator, payment, request);
        });
  }

  @Bean
  ListActiveShiftPayments listActiveShiftPayments(
      SalesAuthorization authorization,
      InvoicePayments payments,
      ObjectProvider<ActiveCashShifts> shifts) {
    return new ListActiveShiftPayments(
        authorization,
        SaleUseCaseConfiguration::actorId,
        (company, operator) -> {
          var finance = shifts.getIfAvailable();
          if (finance == null) {
            throw new PaymentAdmissionUnavailable();
          }
          return finance.find(company, operator);
        },
        payments);
  }

  private static UUID actorId() {
    return AuthenticatedActor.userId().orElseThrow(SalesAccessForbidden::new);
  }

  @Bean
  ListSales listSales(
      SalesAuthorization authorization,
      SaleQueryRepository sales,
      @Value("${sanigest.sales.max-page-size:100}") int maxPageSize) {
    return new ListSales(authorization, sales, maxPageSize);
  }

  @Bean
  CreateSale createSale(
      SalesAuthorization authorization,
      CatalogClient catalog,
      SaleRepository sales,
      InvoiceStock stock) {
    return new CreateSale(
        authorization, catalog, sales, Clock.system(ZoneId.of("America/Santo_Domingo")), stock);
  }

  @Bean
  CreateSaleIdempotently createSaleIdempotently(
      SalesAuthorization authorization,
      CreateSale createSale,
      SaleIdempotencyRepository idempotency) {
    return new CreateSaleIdempotently(
        authorization, SaleUseCaseConfiguration::actorId, createSale, idempotency);
  }
}
