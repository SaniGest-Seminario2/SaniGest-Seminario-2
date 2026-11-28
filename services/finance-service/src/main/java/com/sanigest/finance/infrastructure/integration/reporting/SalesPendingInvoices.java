package com.sanigest.finance.infrastructure.integration.reporting;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sanigest.finance.application.exception.reporting.FinancialSourceFailure;
import com.sanigest.finance.application.exception.reporting.FinancialSourceFailure.Kind;
import com.sanigest.finance.application.port.out.integration.cash.PendingInvoices;
import com.sanigest.security.api.request.company.CompanySelector;
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
        throw new FinancialSourceFailure(Kind.FORBIDDEN, null);
      }
      if (failure.getStatusCode().equals(HttpStatus.UNAUTHORIZED)) {
        throw new FinancialSourceFailure(Kind.UNAUTHENTICATED, null);
      }
      throw unavailable();
    } catch (RestClientException failure) {
      throw unavailable();
    }
  }

  private static FinancialSourceFailure unavailable() {
    return new FinancialSourceFailure(Kind.UNAVAILABLE, null);
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record CollectiblePage(Long totalElements) {}
}
