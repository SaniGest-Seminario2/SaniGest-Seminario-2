package com.sanigest.finance.infrastructure;

import com.sanigest.security.CompanySelector;
import com.sanigest.web.RemoteCall;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
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
  private final Source sales;
  private final Source expense;

  FinancialSources(
      @Value("${sanigest.finance.sales-origin}") String salesOrigin,
      @Value("${sanigest.finance.expense-origin}") String expenseOrigin,
      @Value("${sanigest.finance.source-timeout}") Duration timeout,
      @Nullable MeterRegistry meters) {
    var factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofMillis(500)).build());
    factory.setReadTimeout(timeout);
    sales = source("report-sales", salesOrigin, factory, timeout, meters);
    expense = source("report-expense", expenseOrigin, factory, timeout, meters);
  }

  void authorize(UUID company, String token) {
    sales.request(HttpMethod.HEAD, company, token, RestClient.ResponseSpec::toBodilessEntity);
    expense.request(HttpMethod.HEAD, company, token, RestClient.ResponseSpec::toBodilessEntity);
  }

  Snapshots fetch(UUID company, String token) {
    return new Snapshots(sales.fetch(company, token), expense.fetch(company, token));
  }

  private static Source source(
      String name,
      String origin,
      JdkClientHttpRequestFactory factory,
      Duration timeout,
      @Nullable MeterRegistry meters) {
    return new Source(
        RestClient.builder().baseUrl(origin).requestFactory(factory).build(),
        new RemoteCall(
            name,
            timeout,
            failure ->
                failure instanceof RestClientResponseException response
                    && response.getStatusCode().is4xxClientError()
                    && response.getStatusCode().value() != 429,
            meters));
  }

  private record Source(RestClient client, RemoteCall calls) {
    String fetch(UUID company, String token) {
      return request(
          HttpMethod.GET,
          company,
          token,
          response -> {
            String body = response.body(String.class);
            if (body == null) {
              throw unavailable();
            }
            return body;
          });
    }

    <T> T request(
        HttpMethod method, UUID company, String token, Function<RestClient.ResponseSpec, T> read) {
      try {
        return calls.execute(
            () ->
                read.apply(
                    client
                        .method(method)
                        .uri(PATH)
                        .header(CompanySelector.HEADER, company.toString())
                        .headers(headers -> headers.setBearerAuth(token))
                        .retrieve()),
            FinancialSources::unavailable);
      } catch (RestClientException failure) {
        throw failure(failure);
      }
    }
  }

  private static ResponseStatusException failure(RestClientException failure) {
    if (failure instanceof RestClientResponseException response
        && (response.getStatusCode().value() == 401 || response.getStatusCode().value() == 403)) {
      return new ResponseStatusException(response.getStatusCode());
    }
    return unavailable();
  }

  private static ResponseStatusException unavailable() {
    return new ResponseStatusException(
        HttpStatus.SERVICE_UNAVAILABLE, "Financial source unavailable");
  }

  record Snapshots(String sales, String expense) {}
}
