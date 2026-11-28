package com.sanigest.catalog.infrastructure.integration.expense;

import com.sanigest.catalog.application.port.out.integration.expense.SupplierBalances;
import com.sanigest.security.api.request.company.CompanySelector;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
class ExpenseSupplierBalances implements SupplierBalances {
  private static final int BATCH_SIZE = 100;
  private final RestClient expense;

  ExpenseSupplierBalances(@Value("${CATALOG_EXPENSE_ORIGIN:http://localhost:8087}") String origin) {
    var transport =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofMillis(500))
            .build();
    var factory = new JdkClientHttpRequestFactory(transport);
    factory.setReadTimeout(Duration.ofSeconds(2));
    expense = RestClient.builder().baseUrl(origin).requestFactory(factory).build();
  }

  @Override
  public Map<UUID, BigDecimal> read(UUID companyId, List<UUID> supplierIds) {
    if (!(SecurityContextHolder.getContext().getAuthentication()
        instanceof JwtAuthenticationToken jwt)) {
      throw new Unavailable();
    }
    var balances = new HashMap<UUID, BigDecimal>();
    try {
      for (int start = 0; start < supplierIds.size(); start += BATCH_SIZE) {
        var batch = supplierIds.subList(start, Math.min(start + BATCH_SIZE, supplierIds.size()));
        var rows =
            expense
                .get()
                .uri(uri ->
                    uri.path("/api/v1/payables/supplier-balances")
                        .queryParam("supplierIds", batch)
                        .build())
                .headers(headers -> {
                  headers.setBearerAuth(jwt.getToken().getTokenValue());
                  headers.set(CompanySelector.HEADER, companyId.toString());
                })
                .retrieve()
                .body(new ParameterizedTypeReference<List<Balance>>() {});
        if (rows == null) {
          throw new Unavailable();
        }
        for (var row : rows) {
          balances.put(row.supplierId(), row.outstandingAmount());
        }
        if (!balances.keySet().containsAll(batch)) {
          throw new Unavailable();
        }
      }
      return Map.copyOf(balances);
    } catch (RestClientException failure) {
      throw new Unavailable();
    }
  }

  private record Balance(UUID supplierId, BigDecimal outstandingAmount) {}
}
