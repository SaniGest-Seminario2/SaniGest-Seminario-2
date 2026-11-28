package com.sanigest.sales.infrastructure.integration.inventory;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.sanigest.sales.application.exception.invoice.InvoiceStockConflict;
import com.sanigest.sales.application.exception.invoice.InvoiceStockUnavailable;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class SaleStockClientTest {
  @RegisterExtension
  static final WireMockExtension SERVER =
      WireMockExtension.newInstance().options(options().dynamicPort()).build();

  private final UUID company = UUID.randomUUID();
  private final UUID sale = UUID.randomUUID();
  private final UUID actor = UUID.randomUUID();
  private final Map<UUID, BigDecimal> lines = Map.of(UUID.randomUUID(), BigDecimal.ONE);
  private final String path = "/internal/v1/inventory/companies/" + company + "/sales/" + sale;

  @Test
  void usesTheIntegrationCredentialAndActorForBothEffects() {
    var client = new SaleStockClient(SERVER.baseUrl(), "test-token");
    SERVER.stubFor(post(urlEqualTo(path + "/issue")).willReturn(aResponse().withStatus(204)));
    SERVER.stubFor(post(urlEqualTo(path + "/reverse")).willReturn(aResponse().withStatus(204)));
    assertThat(client.enabled()).isTrue();
    assertThat(new SaleStockClient("", "").enabled()).isFalse();
    client.issue(company, sale, actor, lines);
    client.reverse(company, sale, actor);
    SERVER.verify(
        postRequestedFor(urlEqualTo(path + "/issue"))
            .withHeader("X-Sale-Stock-Token", equalTo("test-token")));
    SERVER.verify(
        postRequestedFor(urlEqualTo(path + "/reverse"))
            .withHeader("X-Sale-Stock-Token", equalTo("test-token"))
            .withHeader("X-Sale-Stock-Actor", equalTo(actor.toString())));
  }

  @Test
  void distinguishesStockConflictsFromUnavailableInventory() {
    var client = new SaleStockClient(SERVER.baseUrl(), "test-token");
    SERVER.stubFor(
        post(urlEqualTo(path + "/issue"))
            .willReturn(
                aResponse()
                    .withStatus(409)
                    .withHeader("Content-Type", "application/problem+json")
                    .withBody("{\"errorCode\":\"INSUFFICIENT_STOCK\"}")));
    assertThatThrownBy(() -> client.issue(company, sale, actor, lines))
        .isInstanceOf(InvoiceStockConflict.class)
        .hasMessage("INSUFFICIENT_STOCK");
    SERVER.stubFor(post(urlEqualTo(path + "/issue")).willReturn(aResponse().withStatus(503)));
    assertThatThrownBy(() -> client.issue(company, sale, actor, lines))
        .isInstanceOf(InvoiceStockUnavailable.class);
  }
}
