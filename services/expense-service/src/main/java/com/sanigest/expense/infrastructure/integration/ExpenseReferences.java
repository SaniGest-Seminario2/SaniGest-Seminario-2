package com.sanigest.expense.infrastructure.integration;

import com.sanigest.expense.application.ExpenseFailure;
import com.sanigest.expense.application.port.out.integration.CatalogReferences;
import com.sanigest.expense.application.port.out.integration.InventoryReceipts;
import com.sanigest.security.CompanySelector;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
class ExpenseReferences implements CatalogReferences, InventoryReceipts {
  private final RestClient catalog;
  private final RestClient inventory;

  ExpenseReferences(
      @Value("${sanigest.catalog-origin}") URI catalogOrigin,
      @Value("${sanigest.inventory-origin}") URI inventoryOrigin) {
    catalog = client(catalogOrigin);
    inventory = client(inventoryOrigin);
  }

  @Override
  public Optional<Supplier> findSupplier(UUID companyId, UUID supplierId) {
    return get(catalog, companyId, SupplierResponse.class, "/api/v1/suppliers/{id}", supplierId)
        .map(response ->
            new Supplier(
                response.id(),
                response.name(),
                "ACTIVE".equals(response.status()),
                response.paymentTerms(),
                response.creditDays()));
  }

  @Override
  public Optional<Item> findItem(UUID companyId, UUID itemId) {
    return get(catalog, companyId, ItemResponse.class, "/api/v1/catalog-items/{id}", itemId)
        .map(response ->
            new Item(
                response.id(),
                response.name(),
                response.active(),
                response.eligibleForPurchase(),
                "PRODUCT".equals(response.type()),
                Boolean.TRUE.equals(response.unitDivisible())));
  }

  @Override
  public Optional<Warehouse> findWarehouse(UUID companyId, UUID warehouseId) {
    return get(
            inventory, companyId, WarehouseResponse.class, "/api/v1/warehouses/{id}", warehouseId)
        .map(response -> new Warehouse(response.id(), "ACTIVE".equals(response.status())));
  }

  @Override
  public boolean tracksStock(UUID companyId, UUID warehouseId, UUID itemId) {
    return get(
            inventory,
            companyId,
            StockResponse.class,
            "/api/v1/inventory/stock/{itemId}/{warehouseId}",
            itemId,
            warehouseId)
        .orElseThrow(() -> ExpenseFailure.invalid("EXPENSE_INVALID_STOCK_REFERENCE"))
        .trackingEnabled();
  }

  @Override
  public UUID recordEntry(Entry entry) {
    try {
      var movement =
          inventory
              .post()
              .uri("/api/v1/inventory/adjustments")
              .headers(headers -> headers.setBearerAuth(token()))
              .header(CompanySelector.HEADER, entry.companyId().toString())
              .header("Idempotency-Key", entry.receiptLineId().toString())
              .body(
                  Map.of(
                      "catalogItemId", entry.itemId(),
                      "warehouseId", entry.warehouseId(),
                      "direction", "IN",
                      "quantity", entry.quantity().stripTrailingZeros().toPlainString(),
                      "reason", "Expense receipt line " + entry.receiptLineId()))
              .retrieve()
              .body(MovementResponse.class);
      if (movement == null || movement.id() == null) {
        throw unavailable("EXPENSE_INVENTORY_RECEIPT_PENDING");
      }
      return movement.id();
    } catch (RestClientException failure) {
      throw unavailable("EXPENSE_INVENTORY_RECEIPT_PENDING");
    }
  }

  private static <T> Optional<T> get(
      RestClient client, UUID company, Class<T> type, String path, Object... parameters) {
    try {
      var response =
          client
              .get()
              .uri(path, parameters)
              .headers(headers -> headers.setBearerAuth(token()))
              .header(CompanySelector.HEADER, company.toString())
              .retrieve()
              .body(type);
      if (response == null) {
        throw unavailable("EXPENSE_REFERENCE_UNAVAILABLE");
      }
      return Optional.of(response);
    } catch (RestClientResponseException failure) {
      if (failure.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
        return Optional.empty();
      }
      if (failure.getStatusCode().equals(HttpStatus.FORBIDDEN)) {
        throw new ExpenseFailure(ExpenseFailure.Kind.FORBIDDEN, "EXPENSE_REFERENCE_ACCESS_DENIED");
      }
      throw unavailable("EXPENSE_REFERENCE_UNAVAILABLE");
    } catch (RestClientException failure) {
      throw unavailable("EXPENSE_REFERENCE_UNAVAILABLE");
    }
  }

  private static String token() {
    var authentication =
        (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
    return authentication.getToken().getTokenValue();
  }

  private static RestClient client(URI origin) {
    var requests = new SimpleClientHttpRequestFactory();
    requests.setConnectTimeout(Duration.ofMillis(500));
    requests.setReadTimeout(Duration.ofSeconds(2));
    return RestClient.builder().baseUrl(origin.toString()).requestFactory(requests).build();
  }

  private static ExpenseFailure unavailable(String code) {
    return new ExpenseFailure(ExpenseFailure.Kind.UNAVAILABLE, code);
  }

  private record SupplierResponse(
      UUID id, String name, String status, String paymentTerms, Integer creditDays) {}

  private record ItemResponse(
      UUID id,
      String name,
      boolean active,
      boolean eligibleForPurchase,
      String type,
      Boolean unitDivisible) {}

  private record WarehouseResponse(UUID id, String status) {}

  private record StockResponse(boolean trackingEnabled) {}

  private record MovementResponse(UUID id) {}
}
