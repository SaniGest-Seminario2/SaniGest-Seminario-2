package com.sanigest.sales.infrastructure.configuration;

import com.sanigest.sales.application.port.out.integration.CatalogClient;
import com.sanigest.sales.application.port.out.integration.SalesAuthorization;
import com.sanigest.sales.application.port.out.persistence.SaleRepository;
import com.sanigest.sales.application.usecase.sale.CreateSale;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class SaleUseCaseConfiguration {
  @Bean
  CreateSale createSale(
      SalesAuthorization authorization, CatalogClient catalog, SaleRepository sales) {
    return new CreateSale(
        authorization, catalog, sales, Clock.system(ZoneId.of("America/Santo_Domingo")));
  }
}
