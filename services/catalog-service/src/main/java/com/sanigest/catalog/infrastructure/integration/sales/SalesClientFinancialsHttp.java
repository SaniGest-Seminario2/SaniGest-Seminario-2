package com.sanigest.catalog.infrastructure.integration.sales;

import com.sanigest.catalog.application.exception.client.ClientAccessException;
import com.sanigest.catalog.application.exception.client.ClientFinancialsUnavailable;
import com.sanigest.catalog.application.port.out.integration.sales.SalesClientFinancials;
import com.sanigest.security.api.request.company.CompanySelector;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
class SalesClientFinancialsHttp implements SalesClientFinancials {
  private static final String BASE = "/internal/client-financials";
  private final RestClient sales;

  SalesClientFinancialsHttp(@Value("${CATALOG_SALES_ORIGIN:http://localhost:8084}") String origin) {
    var transport =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofMillis(500))
            .build();
    var factory = new JdkClientHttpRequestFactory(transport);
    factory.setReadTimeout(Duration.ofSeconds(2));
    sales = RestClient.builder().baseUrl(origin).requestFactory(factory).build();
  }

  @Override
  public Map<UUID, BigDecimal> balances(UUID company, List<UUID> ids) {
    return read(() ->
        retrieve(
                sales
                    .post()
                    .uri(BASE + "/balances?expectedCompanyId={company}", company)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ids),
                company)
            .body(new ParameterizedTypeReference<Map<UUID, BigDecimal>>() {}));
  }

  @Override
  public CompanyTotals summary(UUID company) {
    return read(() ->
        retrieve(sales.get().uri(BASE + "/summary?expectedCompanyId={company}", company), company)
            .body(CompanyTotals.class));
  }

  @Override
  public Totals totals(UUID company, UUID clientId) {
    return read(() ->
        retrieve(
                sales
                    .get()
                    .uri(BASE + "/{id}/summary?expectedCompanyId={company}", clientId, company),
                company)
            .body(Totals.class));
  }

  @Override
  public Statement statement(UUID company, UUID clientId, int page, int size) {
    return read(() ->
        retrieve(
                sales
                    .get()
                    .uri(
                        BASE
                            + "/{id}/statement?expectedCompanyId={company}&page={page}&size={size}",
                        clientId,
                        company,
                        page,
                        size),
                company)
            .body(Statement.class));
  }

  private RestClient.ResponseSpec retrieve(RestClient.RequestHeadersSpec<?> request, UUID company) {
    if (!(SecurityContextHolder.getContext().getAuthentication()
        instanceof JwtAuthenticationToken jwt)) {
      throw forbidden();
    }
    return request
        .headers(headers -> {
          headers.setBearerAuth(jwt.getToken().getTokenValue());
          headers.set(CompanySelector.HEADER, company.toString());
        })
        .retrieve()
        .onStatus(
            status -> !status.is2xxSuccessful(),
            (sent, response) -> {
              int status = response.getStatusCode().value();
              if (status == 401 || status == 403) {
                throw forbidden();
              }
              throw new ClientFinancialsUnavailable();
            });
  }

  private static <T> T read(Supplier<T> request) {
    try {
      T response = request.get();
      if (response != null) {
        return response;
      }
    } catch (RestClientException failure) {
      throw new ClientFinancialsUnavailable();
    }
    throw new ClientFinancialsUnavailable();
  }

  private static ClientAccessException forbidden() {
    return new ClientAccessException(ClientAccessException.Reason.FORBIDDEN);
  }
}
