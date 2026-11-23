package com.sanigest.sales.infrastructure.integration.catalog;

import com.sanigest.sales.infrastructure.integration.catalog.dto.CatalogCustomerResponse;
import com.sanigest.sales.infrastructure.integration.catalog.dto.CatalogItemResponse;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange(accept = "application/json")
interface CatalogHttpApi {
  @GetExchange("/api/v1/clients/{id}")
  ResponseEntity<CatalogCustomerResponse> getCustomer(
      @PathVariable("id") UUID id,
      @RequestParam(value = "expectedCompanyId", required = false) UUID expectedCompanyId);

  @GetExchange("/api/v1/catalog-items/{id}")
  ResponseEntity<CatalogItemResponse> getItem(
      @PathVariable("id") UUID id,
      @RequestParam(value = "expectedCompanyId", required = false) UUID expectedCompanyId);
}
