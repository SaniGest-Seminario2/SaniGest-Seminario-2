package com.sanigest.finance.infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sanigest.finance.application.ActiveCashSummary.PendingInvoices;
import com.sanigest.security.CompanySelector;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Component
class SalesPendingInvoices implements PendingInvoices {
  private final RestClient sales;

  SalesPendingInvoices(@Value("${sanigest.finance.sales-origin}") String origin) {
    var transport = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(500)).build();
    var factory = new JdkClientHttpRequestFactory(transport);
    factory.setReadTimeout(Duration.ofSeconds(2));
    sales = RestClient.builder().baseUrl(origin).requestFactory(factory).build();
  }

  @Override
  public long count(UUID companyId, String accessToken) {
    try {
      var page =
          sales
              .get()
              .uri("/api/v1/invoices/collectible?size=1")
              .header(CompanySelector.HEADER, companyId.toString())
              .headers(headers -> headers.setBearerAuth(accessToken))
              .retrieve()
              .body(CollectiblePage.class);
      if (page != null && page.totalElements() != null && page.totalElements() >= 0) {
        return page.totalElements();
      }
      throw unavailable();
    } catch (RestClientResponseException failure) {
      if (failure.getStatusCode().equals(HttpStatus.FORBIDDEN)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN);
      }
      if (failure.getStatusCode().equals(HttpStatus.UNAUTHORIZED)) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
      }
      throw unavailable();
    } catch (RestClientException failure) {
      throw unavailable();
    }
  }

  private static ResponseStatusException unavailable() {
    return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record CollectiblePage(Long totalElements) {}
}
