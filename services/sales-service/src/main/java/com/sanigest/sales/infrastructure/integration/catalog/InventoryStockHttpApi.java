package com.sanigest.sales.infrastructure.integration.catalog;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sanigest.security.api.request.company.CompanySelector;
import java.util.UUID;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange(accept = "application/json")
interface InventoryStockHttpApi {
  record StockPage(@JsonProperty(required = true) Long totalElements) {}

  @GetExchange("/api/v1/inventory/stock")
  StockPage tracked(
      @RequestHeader(CompanySelector.HEADER) UUID company,
      @RequestParam("catalogItemId") UUID item,
      @RequestParam("trackingEnabled") boolean tracking,
      @RequestParam("size") int size);
}
