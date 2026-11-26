package com.sanigest.inventory.infrastructure.integration.catalog;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange(accept = "application/json")
interface CatalogHttpApi {
  @GetExchange("/api/v1/catalog-items/{id}")
  ResponseEntity<ItemResponse> get(
      @PathVariable("id") UUID itemId,
      @RequestHeader("X-Company-Id") UUID company,
      @RequestParam("expectedCompanyId") UUID expectedCompany,
      @RequestHeader("Authorization") String bearer,
      @RequestHeader("X-Trace-Id") String trace,
      @RequestHeader("traceparent") String traceparent);

  record ItemResponse(
      UUID id,
      String type,
      Boolean active,
      String unitCode,
      Boolean unitDivisible,
      String sku,
      String name) {}
}
