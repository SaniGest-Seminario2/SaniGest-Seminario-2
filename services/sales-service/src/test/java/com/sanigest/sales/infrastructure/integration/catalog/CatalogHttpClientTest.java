package com.sanigest.sales.infrastructure.integration.catalog;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.temporaryRedirect;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.sanigest.sales.application.exception.catalog.CatalogAccessDeniedException;
import com.sanigest.sales.application.exception.catalog.CatalogResourceInvalidException;
import com.sanigest.sales.application.exception.catalog.CatalogUnavailableException;
import com.sanigest.sales.application.port.out.integration.catalog.CatalogClient;
import com.sanigest.sales.domain.model.catalog.CatalogCustomerSnapshot.Status;
import com.sanigest.sales.domain.model.catalog.CatalogItemSnapshot.Type;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class CatalogHttpClientTest {
  private static final UUID ID = UUID.randomUUID();
  private static final UUID TENANT = UUID.randomUUID();
  private static final String CUSTOMER = "/api/v1/clients/" + ID;
  private static final String ITEM = "/api/v1/catalog-items/" + ID;
  private static final String CUSTOMER_JSON =
      """
      {"id":"%s","name":"Cliente","taxId":null,"email":null,"phone":null,"address":null,"status":"ACTIVE"}
      """
          .formatted(ID);
  private static final String ITEM_JSON =
      """
      {"id":"%s","name":"Silla","type":"PRODUCT","description":"Gris","price":3200.10,"currency":"DOP","active":true}
      """
          .formatted(ID);

  @RegisterExtension
  static final WireMockExtension CATALOG =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private CatalogClient client;

  @BeforeEach
  void setUp() {
    client = client(URI.create(CATALOG.baseUrl()), Duration.ofSeconds(2));
    authenticate(Instant.now().plusSeconds(60));
  }

  @AfterEach
  void clearIdentity() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void loadsCustomerUsingAuthenticatedSessionAndSelectedTenant() {
    var expanded =
        CUSTOMER_JSON.replaceFirst(
            "\\{",
            """
            {"clientType":"LEGAL","paymentTerms":"BOTH","creditLimit":"50000.25",
             "preferredReceiptType":"FISCAL_CREDIT","countryCode":"DO","categoryId":null,
            """);
    CATALOG.stubFor(get(urlPathEqualTo(CUSTOMER)).willReturn(okJson(expanded)));
    var snapshot = client.getCustomer(ID, TENANT);
    assertThat(snapshot.id()).isEqualTo(ID);
    assertThat(snapshot.name()).isEqualTo("Cliente");
    assertThat(snapshot.status()).isEqualTo(Status.ACTIVE);
    CATALOG.verify(
        1,
        getRequestedFor(urlPathEqualTo(CUSTOMER))
            .withHeader("Authorization", equalTo("Bearer user-session"))
            .withHeader("X-Company-Id", equalTo(TENANT.toString()))
            .withQueryParam("expectedCompanyId", equalTo(TENANT.toString()))
            .withHeader("X-Company-User-Assertion", absent())
            .withHeader("X-Trace-Id", matching("[0-9a-f]{32}")));
  }

  @ParameterizedTest
  @CsvSource({"PRODUCT,3200.10", "SERVICE,0.00"})
  void loadsActiveItemWithExactPrice(Type type, String price) {
    CATALOG.stubFor(
        get(urlPathEqualTo(ITEM))
            .willReturn(
                okJson(ITEM_JSON.replace("PRODUCT", type.name()).replace("3200.10", price))));
    var snapshot = client.getItem(ID, TENANT);
    assertThat(snapshot.id()).isEqualTo(ID);
    assertThat(snapshot.name()).isEqualTo("Silla");
    assertThat(snapshot.description()).isEqualTo("Gris");
    assertThat(snapshot.type()).isEqualTo(type);
    assertThat(snapshot.price()).isEqualTo(new BigDecimal(price));
    assertThat(snapshot.currency()).isEqualTo("DOP");
    assertThat(snapshot.active()).isTrue();
    CATALOG.verify(1, getRequestedFor(urlPathEqualTo(ITEM)));
  }

  @Test
  void readsTheCatalogDecimalStringForInvoicePricing() {
    var item =
        ITEM_JSON.replace("\"price\":3200.10", "\"price\":3200.1234,\"taxRate\":\"0.180000\"");
    CATALOG.stubFor(get(urlPathEqualTo(ITEM)).willReturn(okJson(item)));

    var snapshot = client.getInvoiceItem(ID, TENANT);
    assertThat(snapshot.price()).isEqualByComparingTo("3200.1234");
    assertThat(snapshot.taxRate()).isEqualByComparingTo("0.18");
  }

  @ParameterizedTest
  @ValueSource(strings = {"invalid", "-0.18", "1", "0.1234567"})
  void rejectsInvalidCatalogTaxRatesForInvoicePricing(String rate) {
    var item = ITEM_JSON.replace("\"price\"", "\"taxRate\":\"" + rate + "\",\"price\"");
    CATALOG.stubFor(get(urlPathEqualTo(ITEM)).willReturn(okJson(item)));

    assertThatThrownBy(() -> client.getInvoiceItem(ID, TENANT))
        .isInstanceOf(CatalogResourceInvalidException.class)
        .hasMessage("Catalog: INVALID_RESPONSE");
  }

  @Test
  void rejectsInactiveResources() {
    CATALOG.stubFor(
        get(urlPathEqualTo(CUSTOMER))
            .willReturn(okJson(CUSTOMER_JSON.replace("ACTIVE", "INACTIVE"))));
    CATALOG.stubFor(
        get(urlPathEqualTo(ITEM)).willReturn(okJson(ITEM_JSON.replace("true", "false"))));
    assertThatThrownBy(() -> client.getCustomer(ID, TENANT))
        .isInstanceOf(CatalogResourceInvalidException.class)
        .hasMessage("Catalog: INACTIVE");
    assertThatThrownBy(() -> client.getItem(ID, TENANT))
        .isInstanceOf(CatalogResourceInvalidException.class)
        .hasMessage("Catalog: INACTIVE");
  }

  @Test
  void acceptsAnExpandedSellableItemAndRejectsAnExplicitlyUnsellableOne() {
    var sellable =
        ITEM_JSON.replace(
            "\"active\":true",
            "\"active\":true,\"availableForSale\":true,\"estimatedCost\":\"10.0001\"");
    CATALOG.stubFor(get(urlPathEqualTo(ITEM)).willReturn(okJson(sellable)));
    assertThat(client.getItem(ID, TENANT).price()).isEqualByComparingTo("3200.10");

    CATALOG.stubFor(
        get(urlPathEqualTo(ITEM))
            .willReturn(
                okJson(
                    sellable.replace("\"availableForSale\":true", "\"availableForSale\":false"))));
    assertThatThrownBy(() -> client.getItem(ID, TENANT))
        .isInstanceOf(CatalogResourceInvalidException.class)
        .hasMessage("Catalog: INACTIVE");
  }

  @Test
  void rejectsInvalidCustomerPayloads() {
    for (String json :
        List.of(
            "{",
            "{}",
            "null",
            CUSTOMER_JSON + "{}",
            CUSTOMER_JSON.replace(ID.toString(), UUID.randomUUID().toString()),
            CUSTOMER_JSON.replace(ID.toString(), "1-1-1-1-1"),
            CUSTOMER_JSON.replace("Cliente", " "),
            CUSTOMER_JSON.replace("ACTIVE", "UNKNOWN"))) {
      CATALOG.stubFor(get(urlPathEqualTo(CUSTOMER)).willReturn(okJson(json)));
      assertThatThrownBy(() -> client.getCustomer(ID, TENANT))
          .isInstanceOf(CatalogResourceInvalidException.class)
          .hasMessage("Catalog: INVALID_RESPONSE");
    }
  }

  @Test
  void rejectsInvalidItemPayloads() {
    for (String json :
        List.of(
            "{}",
            "{",
            ITEM_JSON.replace("PRODUCT", "OTHER"),
            ITEM_JSON.replace(ID.toString(), UUID.randomUUID().toString()),
            ITEM_JSON.replace(",\"active\":true", ""),
            ITEM_JSON.replace("\"description\":\"Gris\",", ""),
            ITEM_JSON.replace("true", "\"true\""),
            ITEM_JSON.replace("3200.10", "\"3200.10\""),
            ITEM_JSON.replace("3200.10", "null"),
            ITEM_JSON.replace("3200.10", "-0.01"),
            ITEM_JSON.replace("3200.10", "0.001"),
            ITEM_JSON.replace("3200.10", "1000000000000"),
            ITEM_JSON.replace("DOP", "USD"))) {
      CATALOG.stubFor(get(urlPathEqualTo(ITEM)).willReturn(okJson(json)));
      assertThatThrownBy(() -> client.getItem(ID, TENANT))
          .isInstanceOf(CatalogResourceInvalidException.class)
          .hasMessage("Catalog: INVALID_RESPONSE");
    }
  }

  @ParameterizedTest
  @CsvSource({
    "400,INVALID_REQUEST", "401,AUTHENTICATION_REJECTED", "403,FORBIDDEN",
    "404,NOT_FOUND_OR_NOT_VISIBLE", "429,RATE_LIMITED", "500,DEPENDENCY_FAILURE",
    "502,DEPENDENCY_FAILURE", "503,DEPENDENCY_FAILURE", "204,DEPENDENCY_FAILURE"
  })
  void translatesStatusWithoutExposingRemoteBody(int status, String reason) {
    var exception =
        switch (status) {
          case 400, 404 -> CatalogResourceInvalidException.class;
          case 401, 403 -> CatalogAccessDeniedException.class;
          default -> CatalogUnavailableException.class;
        };
    CATALOG.stubFor(
        get(urlPathEqualTo(CUSTOMER))
            .willReturn(aResponse().withStatus(status).withBody("private")));
    CATALOG.stubFor(
        get(urlPathEqualTo(ITEM)).willReturn(aResponse().withStatus(status).withBody("private")));
    assertThatThrownBy(() -> client.getCustomer(ID, TENANT))
        .isInstanceOf(exception)
        .hasMessage("Catalog: " + reason)
        .hasNoCause();
    assertThatThrownBy(() -> client.getItem(ID, TENANT))
        .isInstanceOf(exception)
        .hasMessage("Catalog: " + reason)
        .hasNoCause();
    CATALOG.verify(1, getRequestedFor(urlPathEqualTo(CUSTOMER)));
    CATALOG.verify(1, getRequestedFor(urlPathEqualTo(ITEM)));
  }

  @Test
  void failsFastAfterRepeatedCatalogOutages() {
    CATALOG.stubFor(get(urlPathEqualTo(ITEM)).willReturn(aResponse().withStatus(503)));
    for (int call = 0; call < 10; call++) {
      assertThatThrownBy(() -> client.getItem(ID, TENANT))
          .isInstanceOf(CatalogUnavailableException.class)
          .hasMessage("Catalog: DEPENDENCY_FAILURE");
    }

    assertThatThrownBy(() -> client.getCustomer(ID, TENANT))
        .isInstanceOf(CatalogUnavailableException.class);
    CATALOG.verify(10, getRequestedFor(urlPathEqualTo(ITEM)));
    CATALOG.verify(0, getRequestedFor(urlPathEqualTo(CUSTOMER)));
  }

  @Test
  void abortsOnResponseTimeout() {
    CATALOG.stubFor(get(urlPathEqualTo(ITEM)).willReturn(okJson(ITEM_JSON).withFixedDelay(1200)));
    var impatient = client(URI.create(CATALOG.baseUrl()), Duration.ofMillis(300));
    assertThatThrownBy(() -> impatient.getItem(ID, TENANT))
        .isInstanceOf(CatalogUnavailableException.class)
        .hasMessage("Catalog: TRANSPORT_FAILURE");
    CATALOG.verify(1, getRequestedFor(urlPathEqualTo(ITEM)));
  }

  @Test
  void boundsReadingTheEntireResponseBody() {
    CATALOG.stubFor(
        get(urlPathEqualTo(ITEM)).willReturn(okJson(ITEM_JSON).withChunkedDribbleDelay(4, 1200)));
    var impatient = client(URI.create(CATALOG.baseUrl()), Duration.ofMillis(600));
    assertThatThrownBy(() -> impatient.getItem(ID, TENANT))
        .isInstanceOf(CatalogUnavailableException.class)
        .hasMessage("Catalog: TRANSPORT_FAILURE");
  }

  @Test
  void failsClosedOnConnectionRefused() throws Exception {
    int port;
    try (var socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
      port = socket.getLocalPort();
    }
    var offline = client(URI.create("http://127.0.0.1:" + port), Duration.ofSeconds(2));
    assertThatThrownBy(() -> offline.getItem(ID, TENANT))
        .isInstanceOf(CatalogUnavailableException.class)
        .hasMessage("Catalog: TRANSPORT_FAILURE");
  }

  @Test
  void failsBeforeSendingWithoutValidIdentity() {
    SecurityContextHolder.clearContext();
    assertThatThrownBy(() -> client.getItem(ID, TENANT))
        .isInstanceOf(CatalogAccessDeniedException.class)
        .hasMessage("Catalog: MISSING_IDENTITY");
    authenticate(Instant.now().minusSeconds(1));
    assertThatThrownBy(() -> client.getCustomer(ID, TENANT))
        .isInstanceOf(CatalogAccessDeniedException.class)
        .hasMessage("Catalog: MISSING_IDENTITY");
    CATALOG.verify(0, anyRequestedFor(anyUrl()));
  }

  @Test
  void neverFollowsRedirectsToAnotherAuthority() {
    var other = new WireMockServer(0);
    other.start();
    try {
      CATALOG.stubFor(
          get(urlPathEqualTo(ITEM))
              .willReturn(temporaryRedirect(other.baseUrl() + "/redirect-target")));
      assertThatThrownBy(() -> client.getItem(ID, TENANT))
          .isInstanceOf(CatalogUnavailableException.class)
          .hasMessage("Catalog: DEPENDENCY_FAILURE");
      CATALOG.verify(1, getRequestedFor(urlPathEqualTo(ITEM)));
      other.verify(0, anyRequestedFor(anyUrl()));
    } finally {
      other.stop();
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "ftp://localhost",
        "/relative",
        "https://user@localhost",
        "https://localhost/path",
        "https://localhost?tenant=A",
        "https://localhost#foo",
        "https://localhost:0",
        "https://localhost:65536"
      })
  void rejectsInvalidOrigins(String origin) {
    assertThatThrownBy(() -> client(URI.create(origin), Duration.ofSeconds(2)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsCleartextOutsideDevelopmentAndTests() {
    var properties =
        new CatalogClientProperties(
            URI.create(CATALOG.baseUrl()), Duration.ofMillis(500), Duration.ofSeconds(2));
    assertThatThrownBy(() -> properties.validateOrigin(false))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void handlesMissingConfigurationAndInvalidId() {
    var disabled = client(null, Duration.ofSeconds(2));
    assertThatThrownBy(() -> disabled.getItem(ID, TENANT))
        .isInstanceOf(CatalogUnavailableException.class)
        .hasMessage("Catalog: NOT_CONFIGURED");
    assertThatThrownBy(() -> client.getCustomer(null, TENANT))
        .isInstanceOf(CatalogResourceInvalidException.class)
        .hasMessage("Catalog: INVALID_REQUEST");
  }

  @Test
  void rejectsAResponseForAnotherTenant() {
    CATALOG.stubFor(
        get(urlPathEqualTo(CUSTOMER))
            .willReturn(
                com.github.tomakehurst.wiremock.client.WireMock.okJson(CUSTOMER_JSON)
                    .withHeader("X-Catalog-Company-Id", UUID.randomUUID().toString())));
    assertThatThrownBy(() -> client.getCustomer(ID, TENANT))
        .isInstanceOf(CatalogResourceInvalidException.class)
        .hasMessage("Catalog: INVALID_RESPONSE");
  }

  @Test
  void rejectsAConflictingCompanyInThePayloadEvenWithTheCorrectHeader() {
    String payload =
        CUSTOMER_JSON.replaceFirst("\\{", "{\"companyId\":\"" + UUID.randomUUID() + "\",");
    CATALOG.stubFor(get(urlPathEqualTo(CUSTOMER)).willReturn(okJson(payload)));
    assertThatThrownBy(() -> client.getCustomer(ID, TENANT))
        .isInstanceOf(CatalogResourceInvalidException.class)
        .hasMessage("Catalog: INVALID_RESPONSE");
  }

  private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder okJson(
      String body) {
    return com.github.tomakehurst.wiremock.client.WireMock.okJson(body)
        .withHeader("X-Catalog-Company-Id", TENANT.toString());
  }

  private static CatalogClient client(URI origin, Duration timeout) {
    var environment = new MockEnvironment();
    environment.setActiveProfiles("test");
    return new CatalogClientConfiguration()
        .catalogClient(
            new CatalogClientProperties(origin, Duration.ofMillis(500), timeout),
            environment,
            new StaticListableBeanFactory().getBeanProvider(ObservationRegistry.class),
            new StaticListableBeanFactory().getBeanProvider(MeterRegistry.class));
  }

  private static void authenticate(Instant expiresAt) {
    var jwt =
        Jwt.withTokenValue("user-session")
            .header("alg", "RS256")
            .subject(ID.toString())
            .issuedAt(expiresAt.minusSeconds(120))
            .expiresAt(expiresAt)
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
  }
}
