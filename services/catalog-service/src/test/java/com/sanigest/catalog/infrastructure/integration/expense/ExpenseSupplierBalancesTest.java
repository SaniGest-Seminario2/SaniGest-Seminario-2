package com.sanigest.catalog.infrastructure.integration.expense;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.sanigest.catalog.application.port.out.integration.expense.SupplierBalances;
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

class ExpenseSupplierBalancesTest {
  private static final String PATH = "/api/v1/payables/supplier-balances";

  @RegisterExtension
  static final WireMockExtension SERVER =
      WireMockExtension.newInstance().options(options().dynamicPort()).build();

  private final UUID company = UUID.randomUUID();
  private final UUID supplier = UUID.randomUUID();
  private ExpenseSupplierBalances balances;

  @BeforeEach
  void authenticate() {
    balances = new ExpenseSupplierBalances(SERVER.baseUrl());
    var jwt = Jwt.withTokenValue("test-token").header("alg", "RS256").subject("test").build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void sendsCompanyAndBearerAndReadsTheExactBalance() {
    SERVER.stubFor(
        get(urlPathEqualTo(PATH))
            .withHeader("X-Company-Id", equalTo(company.toString()))
            .withHeader("Authorization", equalTo("Bearer test-token"))
            .withQueryParam("supplierIds", equalTo(supplier.toString()))
            .willReturn(
                okJson(
                    """
                    [{"supplierId":"%s","outstandingAmount":12.34}]
                    """
                        .formatted(supplier))));
    assertThat(balances.read(company, List.of(supplier)))
        .containsEntry(supplier, new BigDecimal("12.34"));
  }

  @Test
  void rejectsMissingBalancesAndUnavailableResponses() {
    SERVER.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson("[]")));
    assertUnavailable();
    SERVER.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(204)));
    assertUnavailable();
    SERVER.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(503)));
    assertUnavailable();
    SecurityContextHolder.clearContext();
    assertUnavailable();
  }

  private void assertUnavailable() {
    assertThatThrownBy(() -> balances.read(company, List.of(supplier)))
        .isInstanceOf(SupplierBalances.Unavailable.class);
  }
}
