package com.sanigest.catalog.infrastructure.integration.sales;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.sanigest.catalog.application.exception.ClientAccessException;
import com.sanigest.catalog.application.exception.ClientFinancialsUnavailable;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class SalesClientFinancialsHttpTest {
  private static final String BASE = "/internal/client-financials";

  @RegisterExtension
  static final WireMockExtension SERVER =
      WireMockExtension.newInstance()
          .options(
              com.github.tomakehurst.wiremock.core.WireMockConfiguration.options().dynamicPort())
          .build();

  private final UUID company = UUID.randomUUID();
  private final UUID clientId = UUID.randomUUID();
  private SalesClientFinancialsHttp sales;

  @BeforeEach
  void setUp() {
    sales = new SalesClientFinancialsHttp(SERVER.baseUrl());
    var jwt = Jwt.withTokenValue("test-token").header("alg", "RS256").subject("test").build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void forwardsCompanyAndBearerAndDecodesFinancialResponses() {
    var clientPath = "/" + clientId;
    SERVER.stubFor(
        post(urlEqualTo(path("/balances")))
            .withHeader("X-Company-Id", equalTo(company.toString()))
            .withHeader("Authorization", equalTo("Bearer test-token"))
            .withRequestBody(equalToJson("[\"" + clientId + "\"]"))
            .willReturn(okJson("{\"" + clientId + "\":12.34}")));
    SERVER.stubFor(
        get(urlEqualTo(path("/summary")))
            .willReturn(
                okJson(
                    """
                    {"clientsWithPendingBalance":1,"totalPending":12.34,"asOf":"2026-11-26T12:00:00Z"}
                    """)));
    SERVER.stubFor(
        get(urlEqualTo(path(clientPath + "/summary")))
            .willReturn(
                okJson(
                    """
                    {"balance":12.34,"creditExposure":12.34,"totalInvoiced":20,"paymentsReceived":7.66,
                     "asOf":"2026-11-26T12:00:00Z"}
                    """)));
    SERVER.stubFor(
        get(urlEqualTo(path(clientPath + "/statement") + "&page=1&size=5"))
            .willReturn(
                okJson(
                    """
                    {"invoices":[],"payments":[],"page":1,"size":5,"hasMoreInvoices":true,
                     "hasMorePayments":false,"asOf":"2026-11-26T12:00:00Z"}
                    """)));

    assertThat(sales.balances(company, List.of(clientId)))
        .containsEntry(clientId, new BigDecimal("12.34"));
    assertThat(sales.summary(company).totalPending()).isEqualByComparingTo("12.34");
    assertThat(sales.totals(company, clientId).paymentsReceived()).isEqualByComparingTo("7.66");
    assertThat(sales.statement(company, clientId, 1, 5).hasMoreInvoices()).isTrue();
  }

  @Test
  void distinguishesForbiddenSalesFromUnusableResponses() {
    SERVER.stubFor(get(urlEqualTo(path("/summary"))).willReturn(aResponse().withStatus(403)));
    assertThatThrownBy(() -> sales.summary(company)).isInstanceOf(ClientAccessException.class);

    SERVER.stubFor(get(urlEqualTo(path("/summary"))).willReturn(aResponse().withStatus(503)));
    assertThatThrownBy(() -> sales.summary(company))
        .isInstanceOf(ClientFinancialsUnavailable.class);

    SERVER.stubFor(get(urlEqualTo(path("/summary"))).willReturn(okJson("invalid JSON")));
    assertThatThrownBy(() -> sales.summary(company))
        .isInstanceOf(ClientFinancialsUnavailable.class);

    SERVER.stubFor(get(urlEqualTo(path("/summary"))).willReturn(aResponse().withStatus(204)));
    assertThatThrownBy(() -> sales.summary(company))
        .isInstanceOf(ClientFinancialsUnavailable.class);

    SecurityContextHolder.clearContext();
    assertThatThrownBy(() -> sales.summary(company)).isInstanceOf(ClientAccessException.class);
  }

  private String path(String path) {
    return BASE + path + "?expectedCompanyId=" + company;
  }
}
