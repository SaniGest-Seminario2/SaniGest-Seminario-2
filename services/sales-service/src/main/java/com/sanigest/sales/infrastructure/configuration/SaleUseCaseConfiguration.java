package com.sanigest.sales.infrastructure.configuration;

import com.sanigest.companyaccess.AuthenticatedActor;
import com.sanigest.sales.application.exception.SalesAccessForbidden;
import com.sanigest.sales.application.port.out.integration.CatalogClient;
import com.sanigest.sales.application.port.out.integration.SalesAuthorization;
import com.sanigest.sales.application.port.out.persistence.SaleIdempotencyRepository;
import com.sanigest.sales.application.port.out.persistence.SaleQueryRepository;
import com.sanigest.sales.application.port.out.persistence.SaleRepository;
import com.sanigest.sales.application.usecase.sale.CreateSale;
import com.sanigest.sales.application.usecase.sale.CreateSaleIdempotently;
import com.sanigest.sales.application.usecase.sale.ListSales;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class SaleUseCaseConfiguration {
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
      SaleRepository sales,
      SaleIdempotencyRepository idempotency) {
    return new CreateSaleIdempotently(
        authorization,
        () -> AuthenticatedActor.userId().orElseThrow(SalesAccessForbidden::new),
        createSale,
        sales,
        idempotency);
  }
}
