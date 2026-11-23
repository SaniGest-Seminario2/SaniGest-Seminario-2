package com.sanigest.sales.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.sanigest.companyaccess.CompanyAccessProtocol.PATH;
import static com.sanigest.companyaccess.CompanyAccessProtocol.USER_ASSERTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sanigest.sales.api.controller.SaleController;
import com.sanigest.sales.application.exception.CatalogUnavailableException;
import com.sanigest.sales.application.port.out.integration.CatalogClient;
import com.sanigest.sales.application.port.out.persistence.SaleRepository;
import com.sanigest.sales.domain.enums.SaleMode;
import com.sanigest.sales.domain.enums.SaleStatus;
import com.sanigest.sales.domain.model.Sale;
import com.sanigest.sales.domain.model.SaleLine;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class SalesServiceIT {
  private static final WireMockServer CATALOG = new WireMockServer(0);

  private static final KeyPair USER_KEYS = keys();
  private static final KeyPair SERVICE_KEYS = keys();
  @TempDir static Path credentials;
  @Autowired private SaleController controller;

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17.7-alpine")
          .withDatabaseName("sanigest_sales")
          .withUsername("sanigest_sales");

  @LocalServerPort private int port;
  @Autowired private Environment environment;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private Flyway flyway;
  @Autowired private SaleRepository sales;
  @Autowired private CatalogClient catalog;

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) throws Exception {
    if (!CATALOG.isRunning()) {
      CATALOG.start();
    }
    registry.add("sanigest.catalog.origin", CATALOG::baseUrl);
    registry.add("sanigest.catalog.response-timeout", () -> "500ms");
    registry.add("sanigest.company-access.origin", CATALOG::baseUrl);
    registry.add("sanigest.company-access.enabled", () -> true);
    var publicKey = pem("session", "PUBLIC KEY", USER_KEYS.getPublic().getEncoded());
    var privateKey = pem("service", "PRIVATE KEY", SERVICE_KEYS.getPrivate().getEncoded());
    registry.add("sanigest.jwt.public-key", () -> publicKey);
    registry.add("sanigest.company-access.private-key-location", () -> privateKey);
    registry.add("SALES_DB_HOST", POSTGRES::getHost);
    registry.add("SALES_DB_PORT", () -> POSTGRES.getMappedPort(5432));
    registry.add("SALES_DB_PASSWORD", POSTGRES::getPassword);
  }

  @AfterAll
  static void stopCatalog() {
    CATALOG.stop();
  }

  @Test
  @Tag("smoke")
  void loadsCatalogSnapshotsAndKeepsSalesHealthyWhenCatalogFails() throws Exception {
    var customer = UUID.randomUUID();
    var item = UUID.randomUUID();
    var customerPath = "/api/v1/clients/" + customer;
    var itemPath = "/api/v1/catalog-items/" + item;
    CATALOG.stubFor(
        WireMock.get(customerPath)
            .willReturn(
                okJson(
                    """
                    {"id":"%s","name":"Cliente","taxId":null,"email":null,"phone":null,"address":null,"status":"ACTIVE"}
                    """
                        .formatted(customer))));
    CATALOG.stubFor(
        WireMock.get(itemPath)
            .willReturn(
                okJson(
                    """
                      {"id":"%s","name":"Silla","type":"PRODUCT","description":null,
                    "price":3200.10,"currency":"DOP","active":true}
                    """
                        .formatted(item))));
    var now = Instant.now();
    var jwt =
        Jwt.withTokenValue("smoke-session")
            .header("alg", "RS256")
            .subject(UUID.randomUUID().toString())
            .issuedAt(now)
            .expiresAt(now.plusSeconds(60))
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    try (var http = HttpClient.newHttpClient()) {
      assertThat(catalog.getCustomer(customer).name()).isEqualTo("Cliente");
      assertThat(catalog.getItem(item).price()).isEqualTo(new BigDecimal("3200.10"));
      CATALOG.stubFor(WireMock.get(itemPath).willReturn(aResponse().withStatus(503)));
      assertThatThrownBy(() -> catalog.getItem(item))
          .isInstanceOf(CatalogUnavailableException.class)
          .hasMessage("Catalog: DEPENDENCY_FAILURE");
      assertThat(get(http, "/actuator/health").statusCode()).isEqualTo(200);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  @Test
  @Tag("smoke")
  void startsOnConfiguredPortWithPrivateDatabaseAndMinimalHealth() throws Exception {
    assertThat(port).isPositive();
    assertThat(environment.getProperty("server.port")).isEqualTo("0");
    assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    assertThat(jdbc.queryForObject("SELECT current_database()", String.class))
        .isEqualTo("sanigest_sales");
    assertThat(jdbc.queryForObject("SELECT session_user", String.class))
        .isEqualTo("sanigest_sales");
    assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("2");

    try (HttpClient client = HttpClient.newHttpClient()) {
      for (String path :
          List.of("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness")) {
        HttpResponse<String> response = get(client, path);
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode health = new ObjectMapper().readTree(response.body());
        assertThat(health.path("status").asString()).isEqualTo("UP");
        assertThat(health.propertyNames())
            .allMatch(name -> name.equals("status") || name.equals("groups"));
        if (health.has("groups")) {
          assertThat(health.path("groups").valueStream().map(JsonNode::asString))
              .containsExactlyInAnyOrder("liveness", "readiness");
        }
      }
      assertThat(get(client, "/actuator/env").statusCode()).isEqualTo(401);
    }
  }

  @Test
  @Tag("regression")
  void migratesOnCleanPostgresAndRevalidatesTheSameChecksumWithoutApplyingDdl() {
    assertThat(
            jdbc.queryForList(
                """
                SELECT table_name FROM information_schema.tables WHERE table_schema = 'sales'
                """,
                String.class))
        .containsExactlyInAnyOrder("sales", "sale_lines", "sale_number_sequences");

    assertThat(
            jdbc.queryForList(
                """
                SELECT table_name || '.' || column_name || ':' || data_type || ':' || is_nullable
                FROM information_schema.columns WHERE table_schema = 'sales'
                """,
                String.class))
        .containsExactlyInAnyOrder(
            "sales.id:uuid:NO",
            "sales.code:character varying:YES",
            "sales.company_id:uuid:NO",
            "sales.client_id:uuid:NO",
            "sales.client_name:character varying:NO",
            "sales.sale_date:date:NO",
            "sales.mode:character varying:NO",
            "sales.status:character varying:NO",
            "sales.total:numeric:NO",
            "sale_lines.id:uuid:NO",
            "sale_lines.position:integer:NO",
            "sale_number_sequences.company_id:uuid:NO",
            "sale_number_sequences.sale_year:integer:NO",
            "sale_number_sequences.last_number:bigint:NO",
            "sale_lines.sale_id:uuid:NO",
            "sale_lines.catalog_item_id:uuid:NO",
            "sale_lines.item_name:character varying:NO",
            "sale_lines.item_description:character varying:YES",
            "sale_lines.unit_price:numeric:NO",
            "sale_lines.quantity:numeric:NO",
            "sale_lines.subtotal:numeric:NO");

    assertThat(
            jdbc.queryForList(
                """
                SELECT indexname FROM pg_indexes WHERE schemaname = 'sales'
                """,
                String.class))
        .containsExactlyInAnyOrder(
            "sales_pkey",
            "sale_lines_pkey",
            "sales_company_date_id_idx",
            "sale_lines_sale_id_idx",
            "sales_company_code_key",
            "sale_number_sequences_pkey");
    assertThat(
            jdbc.queryForList(
                """
                SELECT source.relname || '.' || c.conname || ' -> ' || target_schema.nspname || '.' || target.relname
                FROM pg_constraint c
                JOIN pg_class source ON source.oid = c.conrelid
                JOIN pg_namespace source_schema ON source_schema.oid = source.relnamespace
                JOIN pg_class target ON target.oid = c.confrelid
                JOIN pg_namespace target_schema ON target_schema.oid = target.relnamespace
                WHERE c.contype = 'f' AND source_schema.nspname = 'sales'
                """,
                String.class))
        .containsExactly("sale_lines.sale_lines_sale_fk -> sales.sales");

    var history =
        jdbc.queryForList(
            """
            SELECT installed_rank, version, checksum, installed_on, success
            FROM public.flyway_schema_history ORDER BY installed_rank
            """);
    assertThat(history).hasSize(2);
    assertThat(history.getLast()).containsEntry("version", "2").containsEntry("success", true);
    assertThat(history.getFirst()).containsEntry("version", "1").containsEntry("success", true);
    assertThat(history.getFirst().get("checksum")).isInstanceOf(Integer.class);
    flyway.validate();
    assertThat(flyway.migrate().migrationsExecuted).isZero();
    assertThat(
            jdbc.queryForList(
                """
                SELECT installed_rank, version, checksum, installed_on, success
                FROM public.flyway_schema_history ORDER BY installed_rank
                """))
        .isEqualTo(history);
  }

  @Test
  @Tag("regression")
  void upgradesAnExistingV1DatabaseWithoutChangingItsSnapshots() {
    jdbc.execute("CREATE DATABASE sanigest_sales_upgrade");
    var upgrade =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl().replace("/sanigest_sales", "/sanigest_sales_upgrade"),
            POSTGRES.getUsername(),
            POSTGRES.getPassword());
    assertThat(
            Flyway.configure().dataSource(upgrade).target("1").load().migrate().migrationsExecuted)
        .isEqualTo(1);
    var legacy = new JdbcTemplate(upgrade);
    UUID saleId = UUID.randomUUID();
    legacy.update(
        """
        INSERT INTO sales.sales (id, company_id, client_id, client_name, sale_date, mode, status, total)
        VALUES (?, ?, ?, 'Cliente histórico', DATE '2026-11-23', 'CASH', 'REGISTERED', 15.00)
        """,
        saleId,
        UUID.randomUUID(),
        UUID.randomUUID());
    legacy.update(
        """
        INSERT INTO sales.sale_lines
          (id, sale_id, catalog_item_id, item_name, quantity, unit_price, subtotal)
        VALUES (?, ?, ?, 'Servicio histórico', 1.2, 12.50, 15.00)
        """,
        UUID.randomUUID(),
        saleId,
        UUID.randomUUID());

    var current = Flyway.configure().dataSource(upgrade).load();
    assertThat(current.migrate().migrationsExecuted).isEqualTo(1);
    current.validate();
    assertThat(
            legacy.queryForMap(
                "SELECT code, client_name, total FROM sales.sales WHERE id = ?", saleId))
        .containsEntry("code", null)
        .containsEntry("client_name", "Cliente histórico")
        .containsEntry("total", new BigDecimal("15.00"));
    assertThat(
            legacy.queryForMap(
                "SELECT position, item_name, unit_price FROM sales.sale_lines WHERE sale_id = ?",
                saleId))
        .containsEntry("position", 0)
        .containsEntry("item_name", "Servicio histórico")
        .containsEntry("unit_price", new BigDecimal("12.50"));
  }

  @Test
  @Tag("regression")
  void storesExactSnapshotsAndReadsOnlyWithinTheOwningCompany() {
    UUID companyId = UUID.randomUUID();
    UUID clientId = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    UUID saleId = UUID.randomUUID();
    SaleLine first =
        new SaleLine(
            UUID.randomUUID(),
            saleId,
            itemId,
            "Servicio original",
            "Descripción original",
            new BigDecimal("12.50"),
            new BigDecimal("1.2"),
            new BigDecimal("15.00"));
    SaleLine second =
        new SaleLine(
            UUID.randomUUID(),
            saleId,
            UUID.randomUUID(),
            "Otro artículo",
            null,
            new BigDecimal("0.01"),
            BigDecimal.ONE,
            new BigDecimal("0.01"));
    Sale original =
        new Sale(
            saleId,
            companyId,
            clientId,
            "Cliente original",
            LocalDate.of(2026, 11, 23),
            SaleMode.CREDIT,
            SaleStatus.REGISTERED,
            new BigDecimal("15.01"),
            List.of(first, second),
            null);

    sales.insert(original);
    UUID laterId = UUID.randomUUID();
    SaleLine updatedSnapshot =
        new SaleLine(
            UUID.randomUUID(),
            laterId,
            itemId,
            "Servicio nuevo",
            null,
            new BigDecimal("20"),
            BigDecimal.ONE,
            new BigDecimal("20"));
    sales.insert(
        new Sale(
            laterId,
            companyId,
            clientId,
            "Cliente actualizado",
            original.saleDate().plusDays(1),
            SaleMode.CASH,
            SaleStatus.REGISTERED,
            new BigDecimal("20"),
            List.of(updatedSnapshot),
            null));

    Sale saved = sales.findByCompanyIdAndId(companyId, saleId).orElseThrow();
    assertThat(saved).usingRecursiveComparison().ignoringCollectionOrder().isEqualTo(original);
    assertThat(saved.total()).isEqualTo(new BigDecimal("15.01"));
    assertThat(sales.findByCompanyIdAndId(UUID.randomUUID(), saleId)).isEmpty();
  }

  @Test
  @Tag("regression")
  void rollsBackTheEntireSaleWhenALineCannotBeInserted() {
    UUID lineId = UUID.randomUUID();
    UUID company = UUID.randomUUID();
    Sale existing = sales.register(singleLineSale(company, lineId));
    Sale conflicting = singleLineSale(company, lineId);

    assertThatThrownBy(() -> sales.register(conflicting))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(sales.findByCompanyIdAndId(company, conflicting.id())).isEmpty();
    assertThat(sales.findByCompanyIdAndId(company, existing.id())).contains(existing);
    Sale next = sales.register(singleLineSale(company, UUID.randomUUID()));
    assertThat(next.code()).isEqualTo("V-2026-002");
    assertThatThrownBy(() ->
            jdbc.update("UPDATE sales.sales SET code = ? WHERE id = ?", existing.code(), next.id()))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("sales_company_code_key");
  }

  @ParameterizedTest
  @CsvSource({
    "0,1,0,sale_lines_quantity_range",
    "-1,1,0,sale_lines_quantity_range",
    "0.0001,0,0,sale_lines_quantity_range",
    "1000000000000,0,0,sale_lines_quantity_range",
    "1000,1.001,1001,sale_lines_unit_price_amount",
    "0.001,100000000000000000,100000000000000,sale_lines_unit_price_amount",
    "2,50000000000000000,100000000000000000,sale_lines_subtotal_amount",
    "1.001,1,1.001,sale_lines_subtotal_amount",
    "2,1,1,sale_lines_subtotal_exact"
  })
  @Tag("regression")
  void rejectsInvalidDecimalsInPostgres(
      String quantity, String price, String subtotal, String constraint) {
    UUID saleId = insertHeader();
    assertThatThrownBy(() -> insertLine(saleId, quantity, price, subtotal))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining(constraint);
  }

  @ParameterizedTest
  @ValueSource(strings = {"-1", "NaN", "Infinity"})
  @Tag("regression")
  void rejectsNegativeOrNonFinitePriceInPostgres(String price) {
    UUID saleId = insertHeader();
    assertThatThrownBy(() -> insertLine(saleId, "1", price, price))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @Tag("regression")
  void postgresEnforcesTenantOwnershipLifecycleAndLocalParent() {
    UUID saleId = insertHeader();
    assertThatThrownBy(
            () -> jdbc.update("UPDATE sales.sales SET company_id = NULL WHERE id = ?", saleId))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("company_id");
    assertThatThrownBy(
            () -> jdbc.update("UPDATE sales.sales SET status = 'PAID' WHERE id = ?", saleId))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("sales_status_allowed");
    assertThatThrownBy(
            () -> jdbc.update("UPDATE sales.sales SET mode = 'OTHER' WHERE id = ?", saleId))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("sales_mode_allowed");
    assertThatThrownBy(
            () -> jdbc.update("UPDATE sales.sales SET total = 1.001 WHERE id = ?", saleId))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("sales_total_amount");
    assertThatThrownBy(() -> insertLine(UUID.randomUUID(), "1", "1", "1"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("sale_lines_sale_fk");
  }

  @Test
  @Tag("smoke")
  void registersCashWithTwoHistoricalLinesAndRejectsInvalidOrIneligibleSales() throws Exception {
    var fixture = fixture();
    try (var http = HttpClient.newHttpClient()) {
      assertThat(get(http, "/actuator/health").statusCode()).isEqualTo(200);
      assertThat(post(http, fixture, null, fixture.body()).statusCode()).isEqualTo(401);
      assertThat(post(http, fixture, "invalid", fixture.body()).statusCode()).isEqualTo(401);
      assertThat(AopUtils.isAopProxy(controller)).isTrue();
      assertThatThrownBy(() -> controller.create(null))
          .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
      fixture.membership("OWNER", false);
      var response = post(http, fixture, fixture.bearer(), fixture.body());
      assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(201);
      var body = new ObjectMapper().readTree(response.body());
      var id = UUID.fromString(body.path("id").asString());
      assertThat(body.path("code").asString()).isEqualTo("V-2026-001");
      assertThat(body.path("status").asString()).isEqualTo("REGISTERED");
      assertThat(body.path("mode").asString()).isEqualTo("CASH");
      assertThat(body.path("saleDate").asString()).isEqualTo("2026-11-23");
      assertThat(body.path("currency").asString()).isEqualTo("DOP");
      assertThat(body.path("client").path("id").asString()).isEqualTo(fixture.client().toString());
      assertThat(body.path("client").path("name").asString()).isEqualTo("Cliente");
      assertThat(body.path("total").asString()).isEqualTo("12601.50");
      assertThat(body.path("total").isString()).isTrue();
      assertThat(body.has("companyId")).isFalse();
      assertThat(body.path("lines").get(0).path("unitPrice").asString()).isEqualTo("1500.00");
      assertThat(body.path("lines").get(1).path("subtotal").asString()).isEqualTo("9601.50");

      CATALOG.verify(
          WireMock.getRequestedFor(WireMock.urlPathEqualTo(fixture.productPath()))
              .withQueryParam("expectedCompanyId", equalTo(fixture.company().toString()))
              .withHeader("Authorization", equalTo("Bearer " + fixture.bearer())));
      fixture.item(fixture.productPath(), fixture.product(), "PRODUCT", "9999.00", true);
      var stored = sales.findByCompanyIdAndId(fixture.company(), id).orElseThrow();
      assertThat(stored.total()).isEqualTo(new BigDecimal("12601.50"));
      assertThat(stored.lines())
          .extracting(SaleLine::catalogItemId)
          .containsExactly(fixture.product(), fixture.service());
      assertThat(stored.lines())
          .extracting(SaleLine::itemName)
          .containsExactly("PRODUCT original", "SERVICE original");
      assertThat(stored.lines().getFirst().itemDescription()).isEqualTo("Descripción histórica");
      assertThat(stored.lines().getFirst().unitPrice()).isEqualTo(new BigDecimal("1500.00"));
      assertThat(stored.clientId()).isEqualTo(fixture.client());
      assertThat(stored.clientName()).isEqualTo("Cliente");
      assertThat(sales.findByCompanyIdAndId(UUID.randomUUID(), id)).isEmpty();

      for (String invalid :
          List.of(
              fixture.body().replace("\"quantity\":2", "\"quantity\":0"),
              fixture.body().replace("\"quantity\":2", "\"quantity\":2,\"unitPrice\":0"),
              fixture.body().replace("\"mode\"", "\"total\":0,\"mode\""),
              fixture
                  .body()
                  .replace(
                      "\"mode\"", "\"companyId\":\"%s\",\"mode\"".formatted(UUID.randomUUID())))) {
        assertThat(post(http, fixture, fixture.bearer(), invalid).statusCode()).isEqualTo(400);
      }
      fixture.membership("VIEWER", false);
      assertThat(post(http, fixture, fixture.bearer(), fixture.body()).statusCode()).isEqualTo(403);
      fixture.membership("OWNER", false);
      fixture.customer("INACTIVE");
      assertThat(post(http, fixture, fixture.bearer(), fixture.body()).statusCode()).isEqualTo(422);
      fixture.customer("ACTIVE");
      fixture.item(fixture.servicePath(), fixture.service(), "SERVICE", "3200.50", false);
      assertThat(post(http, fixture, fixture.bearer(), fixture.body()).statusCode()).isEqualTo(422);
      assertThat(countSales(fixture.company())).isEqualTo(1);
    }
  }

  @ParameterizedTest
  @CsvSource({
    "ADMIN,false,201",
    "OPERATOR,true,201",
    "OPERATOR,false,403",
    "VIEWER,true,403",
    "ADVISOR,false,403",
    "MISSING,false,403",
    "UNAVAILABLE,false,503"
  })
  @Tag("regression")
  void enforcesCurrentSalesWritePermissionOnTheRealPost(String role, boolean grant, int expected)
      throws Exception {
    var fixture = fixture(role.equals("ADVISOR") ? "ADVISOR" : "REGULAR");
    if (role.equals("MISSING") || role.equals("ADVISOR")) {
      fixture.companyReply(403);
    } else if (role.equals("UNAVAILABLE")) {
      fixture.companyReply(503);
    } else {
      fixture.membership(role, grant);
    }
    try (var http = HttpClient.newHttpClient()) {
      var response = post(http, fixture, fixture.bearer(), fixture.body());
      assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(expected);
      if (expected == 201) {
        fixture.companyReply(403);
        assertThat(post(http, fixture, fixture.bearer(), fixture.body()).statusCode())
            .isEqualTo(403);
      }
      assertThat(countSales(fixture.company())).isEqualTo(expected == 201 ? 1 : 0);
    }
  }

  @ParameterizedTest
  @CsvSource({
    "401,403", "403,403", "404,404", "422,422", "503,503", "timeout,503",
    "currency,503", "negative,503", "incomplete,503", "json,503", "legacy,503", "wrong-tenant,503"
  })
  @Tag("regression")
  void rejectsCatalogFailuresWithoutAllocatingOrSaving(String scenario, int expected)
      throws Exception {
    var fixture = fixture();
    fixture.membership("OWNER", false);
    var item = fixture.itemJson(fixture.service(), "SERVICE", "3200.50", true);
    var answer =
        switch (scenario) {
          case "timeout" -> okJson(item).withFixedDelay(1500);
          case "currency" -> okJson(item.replace("DOP", "USD"));
          case "negative" -> okJson(item.replace("3200.50", "-1.00"));
          case "incomplete" -> okJson(item.replace(",\"active\":true", ""));
          case "json" -> okJson("{");
          case "legacy", "wrong-tenant" -> okJson(item);
          default -> aResponse().withStatus(Integer.parseInt(scenario));
        };
    if (!scenario.equals("legacy")) {
      String tenant =
          scenario.equals("wrong-tenant")
              ? UUID.randomUUID().toString()
              : fixture.company().toString();
      answer.withHeader("X-Catalog-Company-Id", tenant);
    }
    CATALOG.stubFor(
        WireMock.get(WireMock.urlPathEqualTo(fixture.servicePath())).willReturn(answer));
    try (var http = HttpClient.newHttpClient()) {
      assertThat(post(http, fixture, fixture.bearer(), fixture.body()).statusCode())
          .isEqualTo(expected);
      assertThat(countSales(fixture.company())).isZero();
      fixture.item(fixture.servicePath(), fixture.service(), "SERVICE", "3200.50", true);
      var created = post(http, fixture, fixture.bearer(), fixture.body());
      assertThat(created.statusCode()).withFailMessage(created.body()).isEqualTo(201);
      assertThat(new ObjectMapper().readTree(created.body()).path("code").asString())
          .isEqualTo("V-2026-001");
    }
  }

  @Test
  @Tag("regression")
  void allocatesUniqueCodesUnderConcurrentHttpRequestsAndSeparatesTenantAndYear() throws Exception {
    var fixture = fixture();
    fixture.membership("OWNER", false);
    try (var http = HttpClient.newHttpClient();
        var executor = Executors.newFixedThreadPool(6)) {
      List<Callable<HttpResponse<String>>> requests = new ArrayList<>();
      for (int i = 0; i < 12; i++) {
        requests.add(() -> post(http, fixture, fixture.bearer(), fixture.body()));
      }
      var codes = new HashSet<String>();
      for (var future : executor.invokeAll(requests)) {
        var response = future.get();
        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(201);
        assertThat(codes.add(new ObjectMapper().readTree(response.body()).path("code").asString()))
            .isTrue();
      }
      assertThat(countSales(fixture.company())).isEqualTo(12);
      assertThat(codes).contains("V-2026-001", "V-2026-012");

      var nextYear =
          post(http, fixture, fixture.bearer(), fixture.body().replace("2026-11-23", "2027-11-23"));
      assertThat(nextYear.statusCode()).isEqualTo(201);
      assertThat(new ObjectMapper().readTree(nextYear.body()).path("code").asString())
          .isEqualTo("V-2027-001");
      var second = fixture();
      second.membership("OWNER", false);
      var independent = post(http, second, second.bearer(), second.body());
      assertThat(independent.statusCode()).isEqualTo(201);
      assertThat(new ObjectMapper().readTree(independent.body()).path("code").asString())
          .isEqualTo("V-2026-001");
    }
  }

  private long countSales(UUID company) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM sales.sales WHERE company_id = ?", Long.class, company);
  }

  private HttpResponse<String> post(HttpClient http, Fixture fixture, String bearer, String body)
      throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/sales"))
            .header("Content-Type", "application/json")
            .header("X-Company-Id", fixture.user().toString())
            .timeout(Duration.ofSeconds(10))
            .POST(HttpRequest.BodyPublishers.ofString(body));
    if (bearer != null) {
      request.header("Authorization", "Bearer " + bearer);
    }
    return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private static Fixture fixture() throws Exception {
    return fixture("REGULAR");
  }

  private static Fixture fixture(String accountType) throws Exception {
    UUID user = UUID.randomUUID();
    var claims =
        new JWTClaimsSet.Builder()
            .issuer("sanigest-test")
            .audience("sanigest-api-test")
            .subject(user.toString())
            .issueTime(Date.from(Instant.now()))
            .expirationTime(Date.from(Instant.now().plusSeconds(120)))
            .claim("role", "OWNER")
            .claim("accountType", accountType)
            .build();
    var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
    jwt.sign(new RSASSASigner(USER_KEYS.getPrivate()));
    var fixture =
        new Fixture(
            user,
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            jwt.serialize());
    fixture.customer("ACTIVE");
    fixture.item(fixture.productPath(), fixture.product(), "PRODUCT", "1500.00", true);
    fixture.item(fixture.servicePath(), fixture.service(), "SERVICE", "3200.50", true);
    return fixture;
  }

  private record Fixture(
      UUID user, UUID company, UUID client, UUID product, UUID service, String bearer) {
    String productPath() {
      return "/api/v1/catalog-items/" + product;
    }

    String servicePath() {
      return "/api/v1/catalog-items/" + service;
    }

    String body() {
      return """
      {"clientId":"%s","lines":[{"catalogItemId":"%s","quantity":2},
      {"catalogItemId":"%s","quantity":3}],"saleDate":"2026-11-23","mode":"CASH"}
      """
          .formatted(client, product, service);
    }

    void customer(String status) {
      catalog(
          "/api/v1/clients/" + client,
          """
          {"id":"%s","name":"Cliente","status":"%s"}
          """
              .formatted(client, status));
    }

    void item(String path, UUID id, String type, String price, boolean active) {
      catalog(path, itemJson(id, type, price, active));
    }

    String itemJson(UUID id, String type, String price, boolean active) {
      return """
      {"id":"%s","name":"%s original","type":"%s","description":"Descripción histórica",
      "price":%s,"currency":"DOP","active":%s}
      """
          .formatted(id, type, type, price, active);
    }

    void catalog(String path, String body) {
      CATALOG.stubFor(
          WireMock.get(WireMock.urlPathEqualTo(path))
              .withQueryParam("expectedCompanyId", equalTo(company.toString()))
              .withHeader("Authorization", equalTo("Bearer " + bearer))
              .willReturn(okJson(body).withHeader("X-Catalog-Company-Id", company.toString())));
    }

    void membership(String role, boolean grant) {
      CATALOG.stubFor(
          WireMock.get(PATH)
              .withHeader(USER_ASSERTION, equalTo(bearer))
              .willReturn(
                  okJson(
                      """
                      {"userId":"%s","companyId":"%s","module":"SALES","scope":"WRITE",
                      "mode":"MEMBERSHIP","role":"%s","explicitGrant":%s}
                      """
                          .formatted(user, company, role, grant))));
    }

    void companyReply(int status) {
      CATALOG.stubFor(
          WireMock.get(PATH)
              .withHeader(USER_ASSERTION, equalTo(bearer))
              .willReturn(aResponse().withStatus(status)));
    }
  }

  private static KeyPair keys() {
    try {
      var generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (GeneralSecurityException failure) {
      throw new IllegalStateException(failure);
    }
  }

  private static String pem(String name, String label, byte[] encoded) throws Exception {
    return Files.writeString(
            credentials.resolve(name + ".pem"),
            "-----BEGIN "
                + label
                + "-----\n"
                + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded)
                + "\n-----END "
                + label
                + "-----\n")
        .toUri()
        .toString();
  }

  private HttpResponse<String> get(HttpClient client, String path) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build();
    return client.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private UUID insertHeader() {
    UUID saleId = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO sales.sales (id, company_id, client_id, client_name, sale_date, mode, status, total)
        VALUES (?, ?, ?, 'Cliente', DATE '2026-11-23', 'CASH', 'REGISTERED', 0)
        """,
        saleId,
        UUID.randomUUID(),
        UUID.randomUUID());
    return saleId;
  }

  private void insertLine(UUID saleId, String quantity, String price, String subtotal) {
    jdbc.update(
        """
        INSERT INTO sales.sale_lines
          (id, sale_id, catalog_item_id, item_name, quantity, unit_price, subtotal)
        VALUES (?, ?, ?, 'Producto', CAST(? AS numeric), CAST(? AS numeric), CAST(? AS numeric))
        """,
        UUID.randomUUID(),
        saleId,
        UUID.randomUUID(),
        quantity,
        price,
        subtotal);
  }

  private Sale singleLineSale(UUID company, UUID lineId) {
    UUID saleId = UUID.randomUUID();
    SaleLine line =
        new SaleLine(
            lineId,
            saleId,
            UUID.randomUUID(),
            "Producto",
            null,
            BigDecimal.ONE,
            BigDecimal.ONE,
            BigDecimal.ONE);
    return new Sale(
        saleId,
        company,
        UUID.randomUUID(),
        "Cliente",
        LocalDate.of(2026, 11, 23),
        SaleMode.CASH,
        SaleStatus.REGISTERED,
        BigDecimal.ONE,
        List.of(line),
        null);
  }
}
