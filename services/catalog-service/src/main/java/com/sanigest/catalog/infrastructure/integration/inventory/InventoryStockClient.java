package com.sanigest.catalog.infrastructure.integration.inventory;

import com.sanigest.catalog.application.exception.inventory.InventoryLookupFailed;
import com.sanigest.catalog.application.exception.inventory.InventoryLookupFailed.Reason;
import com.sanigest.catalog.application.port.out.integration.inventory.InventoryAvailability;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
final class InventoryStockClient implements InventoryAvailability {
  private final RestClient client;

  InventoryStockClient(@Value("${CATALOG_INVENTORY_ORIGIN:http://localhost:8086}") String origin) {
    var factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofMillis(250)).build());
    factory.setReadTimeout(Duration.ofMillis(800));
    client = RestClient.builder().baseUrl(origin).requestFactory(factory).build();
  }

  @Override
  public void verifyWarehouse(UUID companyId, UUID warehouseId) {
    try {
      retrieve(client.get().uri("/api/v1/warehouses/{id}", warehouseId), companyId)
          .toBodilessEntity();
    } catch (RestClientException failure) {
      throw new InventoryLookupFailed(Reason.UNAVAILABLE);
    }
  }

  @Override
  public Map<UUID, State> positions(UUID companyId, UUID warehouseId, List<UUID> itemIds) {
    var request =
        client
            .get()
            .uri(uri ->
                uri.path("/api/v1/inventory/stock")
                    .queryParam("warehouseId", warehouseId)
                    .queryParam(
                        "catalogItemIds",
                        String.join(",", itemIds.stream().map(UUID::toString).toList()))
                    .queryParam("size", itemIds.size())
                    .build());
    try {
      var result = retrieve(request, companyId).body(StockPage.class);
      if (result == null
          || result.content() == null
          || result.totalElements() == null
          || result.totalElements() != result.content().size()) {
        throw new InventoryLookupFailed(Reason.UNAVAILABLE);
      }
      var requested = new HashSet<>(itemIds);
      var states = new HashMap<UUID, State>();
      for (var position : result.content()) {
        if (position == null
            || !warehouseId.equals(position.warehouseId())
            || !requested.contains(position.catalogItemId())
            || position.availability() == null
            || position.availability() == State.NOT_APPLICABLE
            || states.putIfAbsent(position.catalogItemId(), position.availability()) != null) {
          throw new InventoryLookupFailed(Reason.UNAVAILABLE);
        }
      }
      return states;
    } catch (RestClientException failure) {
      throw new InventoryLookupFailed(Reason.UNAVAILABLE);
    }
  }

  private RestClient.ResponseSpec retrieve(
      RestClient.RequestHeadersSpec<?> request, UUID companyId) {
    if (!(SecurityContextHolder.getContext().getAuthentication()
        instanceof JwtAuthenticationToken jwt)) {
      throw new InventoryLookupFailed(Reason.FORBIDDEN);
    }
    return request
        .headers(headers -> {
          headers.setBearerAuth(jwt.getToken().getTokenValue());
          headers.set("X-Company-Id", companyId.toString());
        })
        .retrieve()
        .onStatus(
            status -> !status.is2xxSuccessful(),
            (sent, response) -> {
              throw new InventoryLookupFailed(
                  switch (response.getStatusCode().value()) {
                    case 403 -> Reason.FORBIDDEN;
                    case 404 -> Reason.WAREHOUSE_NOT_FOUND;
                    default -> Reason.UNAVAILABLE;
                  });
            });
  }

  private record StockPage(List<Position> content, Long totalElements) {}

  private record Position(UUID catalogItemId, UUID warehouseId, State availability) {}
}
