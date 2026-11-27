package com.sanigest.finance.infrastructure;

import com.sanigest.security.CompanySelector;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Component
class FinancialSources {
  private static final String PATH = "/internal/financial-snapshot/v1";
  private final RestClient sales;
  private final RestClient expense;

  FinancialSources(
      @Value("${sanigest.finance.sales-origin}") String salesOrigin,
      @Value("${sanigest.finance.expense-origin}") String expenseOrigin) {
    var factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofMillis(500)).build());
    factory.setReadTimeout(Duration.ofSeconds(5));
    sales = RestClient.builder().baseUrl(salesOrigin).requestFactory(factory).build();
    expense = RestClient.builder().baseUrl(expenseOrigin).requestFactory(factory).build();
  }

  void authorize(UUID company, String token) {
    request(sales, HttpMethod.HEAD, company, token).toBodilessEntity();
    request(expense, HttpMethod.HEAD, company, token).toBodilessEntity();
  }

  Snapshots fetch(UUID company, String token) {
    return new Snapshots(fetch(sales, company, token), fetch(expense, company, token));
  }

  private static String fetch(RestClient client, UUID company, String token) {
    String body = request(client, HttpMethod.GET, company, token).body(String.class);
    if (body == null) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
    }
    return body;
  }

  private static RestClient.ResponseSpec request(
      RestClient client, HttpMethod method, UUID company, String token) {
    return client
        .method(method)
        .uri(PATH)
        .header(CompanySelector.HEADER, company.toString())
        .headers(headers -> headers.setBearerAuth(token))
        .retrieve();
  }

  static ResponseStatusException failure(RestClientException failure) {
    if (failure instanceof RestClientResponseException response
        && (response.getStatusCode().value() == 401 || response.getStatusCode().value() == 403)) {
      return new ResponseStatusException(response.getStatusCode());
    }
    return new ResponseStatusException(
        HttpStatus.SERVICE_UNAVAILABLE, "Financial source unavailable");
  }

  record Snapshots(String sales, String expense) {}
}
