package com.sanigest.sales.infrastructure.configuration;

import com.sanigest.companyaccess.AuthenticatedActor;
import com.sanigest.sales.application.exception.SalesAccessForbidden;
import com.sanigest.sales.application.port.out.integration.CatalogClient;
import com.sanigest.sales.application.port.out.integration.SalesAuthorization;
import com.sanigest.sales.application.port.out.persistence.ReceivableRepository;
import com.sanigest.sales.application.port.out.persistence.SaleIdempotencyRepository;
import com.sanigest.sales.application.port.out.persistence.SaleQueryRepository;
import com.sanigest.sales.application.port.out.persistence.SaleRepository;
import com.sanigest.sales.application.usecase.receivable.GetReceivable;
import com.sanigest.sales.application.usecase.sale.CancelSale;
import com.sanigest.sales.application.usecase.sale.CreateSale;
import com.sanigest.sales.application.usecase.sale.CreateSaleIdempotently;
import com.sanigest.sales.application.usecase.sale.GetSaleDetail;
import com.sanigest.sales.application.usecase.sale.ListSales;
import java.time.Clock;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class SaleUseCaseConfiguration {
  @Bean
  GetReceivable getReceivable(SalesAuthorization authorization, ReceivableRepository receivables) {
    return new GetReceivable(authorization, receivables);
  }

  @Bean
  GetSaleDetail getSaleDetail(SalesAuthorization authorization, SaleRepository sales) {
    return new GetSaleDetail(authorization, sales);
  }

  @Bean
  CancelSale cancelSale(SalesAuthorization authorization, SaleRepository sales) {
    return new CancelSale(
        authorization, SaleUseCaseConfiguration::actorId, sales, Clock.systemUTC());
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
      SalesAuthorization authorization, CatalogClient catalog, SaleRepository sales) {
    return new CreateSale(
        authorization, catalog, sales, Clock.system(ZoneId.of("America/Santo_Domingo")));
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
