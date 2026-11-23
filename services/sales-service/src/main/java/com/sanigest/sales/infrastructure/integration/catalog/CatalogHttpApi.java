package com.sanigest.sales.infrastructure.integration.catalog;

import com.sanigest.sales.infrastructure.integration.catalog.dto.CatalogCustomerResponse;
import com.sanigest.sales.infrastructure.integration.catalog.dto.CatalogItemResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange(accept = "application/json")
interface CatalogHttpApi {
  @GetExchange("/api/v1/clients/{id}")
  CatalogCustomerResponse getCustomer(@PathVariable("id") UUID id);

  @GetExchange("/api/v1/catalog-items/{id}")
  CatalogItemResponse getItem(@PathVariable("id") UUID id);
}
