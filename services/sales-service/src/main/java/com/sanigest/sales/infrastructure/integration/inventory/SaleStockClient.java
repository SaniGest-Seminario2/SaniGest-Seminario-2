package com.sanigest.sales.infrastructure.integration.inventory;

import com.sanigest.sales.application.exception.invoice.InvoiceStockConflict;
import com.sanigest.sales.application.exception.invoice.InvoiceStockUnavailable;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

@Component
public class SaleStockClient {
  private static final String PATH = "/internal/v1/inventory/companies/{company}/sales/{sale}";
  private final RestClient inventory;
  private final boolean enabled;

  SaleStockClient(
      @Value("${sanigest.inventory.origin:}") String origin,
      @Value("${SALE_STOCK_TOKEN:}") String token) {
    enabled = !token.isBlank();
    if (enabled && (origin.isBlank() || !URI.create(origin).isAbsolute())) {
      throw new IllegalArgumentException("SALE_STOCK_TOKEN requires a configured Inventory origin");
    }
    var transport =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofMillis(500))
            .build();
    var factory = new JdkClientHttpRequestFactory(transport);
    factory.setReadTimeout(Duration.ofSeconds(2));
    inventory =
        RestClient.builder()
            .baseUrl(origin)
            .requestFactory(factory)
            .defaultHeader("X-Sale-Stock-Token", token)
            .build();
  }

  public boolean enabled() {
    return enabled;
  }

  public void issue(UUID company, UUID sale, UUID actor, Map<UUID, BigDecimal> quantities) {
    try {
      inventory
          .post()
          .uri(PATH + "/issue", company, sale)
          .body(new Issue(actor, quantities))
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientResponseException failure) {
      if (failure.getStatusCode().value() == 409 || failure.getStatusCode().value() == 400) {
        throw new InvoiceStockConflict(stockError(failure));
      }
      throw new InvoiceStockUnavailable(failure);
    } catch (RestClientException failure) {
      throw new InvoiceStockUnavailable(failure);
    }
  }

  private static String stockError(RestClientResponseException failure) {
    var problem = failure.getResponseBodyAs(JsonNode.class);
    var code = problem == null ? "" : problem.path("errorCode").asString("");
    return switch (code) {
      case "INSUFFICIENT_STOCK", "SALE_STOCK_WAREHOUSE_AMBIGUOUS", "WAREHOUSE_INACTIVE" -> code;
      default -> "SALE_STOCK_CONFLICT";
    };
  }

  public void reverse(UUID company, UUID sale, UUID actor) {
    inventory
        .post()
        .uri(PATH + "/reverse", company, sale)
        .header("X-Sale-Stock-Actor", actor.toString())
        .retrieve()
        .toBodilessEntity();
  }

  private record Issue(UUID actor, Map<UUID, BigDecimal> quantities) {}
}
