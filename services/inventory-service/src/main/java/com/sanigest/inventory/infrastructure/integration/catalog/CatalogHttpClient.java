package com.sanigest.inventory.infrastructure.integration.catalog;

import com.sanigest.companyaccess.AuthenticatedActor;
import com.sanigest.inventory.application.port.out.integration.CatalogInventoryClient;
import com.sanigest.inventory.domain.exception.InventoryViolation;
import com.sanigest.inventory.domain.exception.InventoryViolation.Kind;
import com.sanigest.inventory.observability.InventoryRequestTrace;
import java.util.List;
import java.util.UUID;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

final class CatalogHttpClient implements CatalogInventoryClient {
  private final CatalogHttpApi api;

  CatalogHttpClient(CatalogHttpApi api) {
    this.api = api;
  }

  @Override
  public Item find(UUID companyId, UUID itemId) {
    if (AuthenticatedActor.userId().isEmpty()
        || !(org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication()
            instanceof
            org.springframework.security.oauth2.server.resource.authentication
                    .JwtAuthenticationToken
                actor)) {
      throw new InventoryViolation(Kind.FORBIDDEN, "CATALOG_ACCESS_DENIED");
    }
    var trace = InventoryRequestTrace.current();
    var span = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    try {
      var response =
          api.get(
              itemId,
              companyId,
              companyId,
              "Bearer " + actor.getToken().getTokenValue(),
              trace,
              "00-" + trace + "-" + span + "-00");
      var item = response.getBody();
      if (!verified(response, companyId) || !validItem(item, itemId)) {
        throw unavailable();
      }
      return new Item(
          item.id(),
          item.type(),
          item.active(),
          item.unitCode(),
          item.unitDivisible(),
          item.sku(),
          item.name());
    } catch (RestClientResponseException failure) {
      throw switch (failure.getStatusCode().value()) {
        case 404 -> new InventoryViolation(Kind.MISSING, "CATALOG_ITEM_NOT_FOUND");
        case 401, 403 -> new InventoryViolation(Kind.FORBIDDEN, "CATALOG_ACCESS_DENIED");
        default -> unavailable();
      };
    } catch (RestClientException failure) {
      throw unavailable();
    }
  }

  private static boolean verified(
      org.springframework.http.ResponseEntity<CatalogHttpApi.ItemResponse> response, UUID company) {
    return response.getStatusCode().value() == 200
        && List.of(company.toString())
            .equals(response.getHeaders().getOrEmpty("X-Catalog-Company-Id"));
  }

  private static boolean validItem(CatalogHttpApi.ItemResponse item, UUID id) {
    return item != null
        && id.equals(item.id())
        && item.active() != null
        && ("PRODUCT".equals(item.type()) || "SERVICE".equals(item.type()))
        && item.name() != null
        && !item.name().isBlank();
  }

  private static InventoryViolation unavailable() {
    return new InventoryViolation(Kind.UNAVAILABLE, "CATALOG_UNAVAILABLE");
  }
}
