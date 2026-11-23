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
import com.sanigest.sales.SalesServiceApplication;
import com.sanigest.sales.api.controller.SaleController;
import com.sanigest.sales.application.exception.CatalogUnavailableException;
import com.sanigest.sales.application.exception.IdempotencyInProgress;
import com.sanigest.sales.application.port.out.integration.CatalogClient;
import com.sanigest.sales.application.port.out.persistence.SaleIdempotencyRepository;
import com.sanigest.sales.application.port.out.persistence.SaleIdempotencyRepository.Claim;
import com.sanigest.sales.application.port.out.persistence.SaleIdempotencyRepository.Request;
import com.sanigest.sales.application.port.out.persistence.SaleRepository;
import com.sanigest.sales.domain.enums.SaleMode;
import com.sanigest.sales.domain.enums.SaleStatus;
import com.sanigest.sales.domain.model.Sale;
import com.sanigest.sales.domain.model.SaleLine;
import jakarta.persistence.EntityManagerFactory;
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
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
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
  @Autowired private EntityManagerFactory entityManagerFactory;
  @Autowired private SaleRepository sales;
  @Autowired private SaleIdempotencyRepository idempotency;
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
    assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("5");

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
        .containsExactlyInAnyOrder(
            "sales", "sale_lines", "sale_number_sequences", "sale_idempotency_keys", "receivables");

    assertThat(
            jdbc.queryForList(
                """
                SELECT table_name || '.' || column_name || ':' || data_type || ':' || is_nullable
                FROM information_schema.columns WHERE table_schema = 'sales'
                  AND table_name NOT IN ('sale_idempotency_keys', 'receivables')
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
            "sales.cancelled_by:uuid:YES",
            "sales.cancelled_at:timestamp with time zone:YES",
            "sales.cancel_reason:character varying:YES",
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
            "sale_number_sequences_pkey",
            "sale_idempotency_keys_pkey",
            "sale_idempotency_expiration_idx",
            "sales_company_id_sale_date_key",
            "receivables_pkey",
            "receivables_sale_id_key");
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
        .containsExactlyInAnyOrder(
            "sale_lines.sale_lines_sale_fk -> sales.sales",
            "sale_idempotency_keys.sale_idempotency_sale_fk -> sales.sales",
            "receivables.receivables_sale_fk -> sales.sales");

    var history =
        jdbc.queryForList(
            """
            SELECT installed_rank, version, checksum, installed_on, success
            FROM public.flyway_schema_history ORDER BY installed_rank
            """);
    assertThat(history).hasSize(5);
    assertThat(history.getLast()).containsEntry("version", "5").containsEntry("success", true);
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
        VALUES (?, ?, ?, 'Cliente histórico', DATE '2026-11-23', 'CREDIT', 'REGISTERED', 15.00)
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
    assertThat(
            Flyway.configure().dataSource(upgrade).target("2").load().migrate().migrationsExecuted)
        .isEqualTo(1);
    assertThat(
            Flyway.configure().dataSource(upgrade).target("3").load().migrate().migrationsExecuted)
        .isEqualTo(1);
    UUID legacyCancellation = UUID.randomUUID();
    legacy.update(
        """
        INSERT INTO sales.sales (id, company_id, client_id, client_name, sale_date, mode, status, total)
        SELECT ?, company_id, client_id, client_name, sale_date, mode, 'CANCELLED', total
          FROM sales.sales WHERE id = ?
        """,
        legacyCancellation,
        saleId);
    assertThat(
            Flyway.configure().dataSource(upgrade).target("4").load().migrate().migrationsExecuted)
        .isEqualTo(1);
    assertThat(current.migrate().migrationsExecuted).isEqualTo(1);
    assertThat(legacy.queryForObject("SELECT count(*) FROM sales.receivables", Long.class))
        .isZero();
    assertThat(current.info().current().getVersion().getVersion()).isEqualTo("5");
    assertThat(
            legacy.queryForMap(
                "SELECT status, cancelled_by, cancelled_at, cancel_reason FROM sales.sales WHERE id = ?",
                legacyCancellation))
        .containsEntry("status", "CANCELLED")
        .containsEntry("cancelled_by", null)
        .containsEntry("cancelled_at", null)
        .containsEntry("cancel_reason", null);
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
            null,
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
            null,
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
            () -> jdbc.update("UPDATE sales.sales SET status = 'CANCELLED' WHERE id = ?", saleId))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("sales_cancellation_metadata");
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
      assertThatThrownBy(() -> controller.create(null, null))
          .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
      fixture.membership("OWNER", false);
      assertThat(post(http, fixture, fixture.bearer(), fixture.body(), null).statusCode())
          .isEqualTo(400);
      String key = UUID.randomUUID().toString();
      var response = post(http, fixture, fixture.bearer(), fixture.body(), key);
      assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(201);
      var body = new ObjectMapper().readTree(response.body());
      var id = UUID.fromString(body.path("id").asString());
      assertThat(body.path("code").asString()).isEqualTo("V-2026-001");
      assertThat(body.path("status").asString()).isEqualTo("REGISTERED");
      assertThat(body.path("mode").asString()).isEqualTo("CASH");
      assertThat(countReceivables(fixture.company())).isZero();
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
      var replay =
          post(
              http,
              fixture,
              fixture.bearer(),
              fixture.body().replace("\"quantity\":2", "\"quantity\":2.0000"),
              key);
      assertThat(replay.statusCode()).isEqualTo(201);
      assertThat(replay.body()).isEqualTo(response.body());
      var conflict =
          post(http, fixture, fixture.bearer(), fixture.body().replace("CASH", "CREDIT"), key);
      assertThat(conflict.statusCode()).isEqualTo(409);
      assertThat(conflict.body()).contains("IDEMPOTENCY_CONFLICT");
      assertThat(post(http, fixture, null, fixture.body(), key).statusCode()).isEqualTo(401);
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
      assertThat(post(http, fixture, fixture.bearer(), fixture.body(), key).statusCode())
          .isEqualTo(403);
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
      String key = UUID.randomUUID().toString();
      var response = post(http, fixture, fixture.bearer(), fixture.body(), key);
      assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(expected);
      if (expected == 201) {
        fixture.companyReply(403);
        assertThat(post(http, fixture, fixture.bearer(), fixture.body(), key).statusCode())
            .isEqualTo(403);
        fixture.companyReply(503);
        assertThat(post(http, fixture, fixture.bearer(), fixture.body(), key).statusCode())
            .isEqualTo(503);
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
      String key = UUID.randomUUID().toString();
      assertThat(post(http, fixture, fixture.bearer(), fixture.body(), key).statusCode())
          .isEqualTo(expected);
      assertThat(countSales(fixture.company())).isZero();
      fixture.item(fixture.servicePath(), fixture.service(), "SERVICE", "3200.50", true);
      var created = post(http, fixture, fixture.bearer(), fixture.body(), key);
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

  @ParameterizedTest
  @CsvSource({"2,false", "10,false", "2,true"})
  @Tag("regression")
  void concurrentRequestsPersistOneResponse(int participants, boolean differentPayload)
      throws Exception {
    var fixture = fixture();
    fixture.membership("OWNER", false);
    String key = UUID.randomUUID().toString();
    String changed = creditBody(fixture);
    var barrier = new CyclicBarrier(participants);
    try (var http = HttpClient.newHttpClient();
        var executor = Executors.newFixedThreadPool(participants)) {
      List<Callable<HttpResponse<String>>> requests = new ArrayList<>();
      for (int i = 0; i < participants; i++) {
        String payload = !differentPayload || i == 0 ? changed : fixture.body();
        requests.add(() -> {
          barrier.await(5, TimeUnit.SECONDS);
          return post(http, fixture, fixture.bearer(), payload, key);
        });
      }
      var responses = new ArrayList<HttpResponse<String>>();
      for (var future : executor.invokeAll(requests, 15, TimeUnit.SECONDS)) {
        responses.add(future.get());
      }
      var winner =
          responses.stream().filter(result -> result.statusCode() == 201).findFirst().orElseThrow();
      for (var response : responses) {
        if (response.statusCode() == 201) {
          assertThat(response.body()).isEqualTo(winner.body());
        } else {
          assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(409);
          assertThat(response.body())
              .contains(differentPayload ? "IDEMPOTENCY_CONFLICT" : "IDEMPOTENCY_IN_PROGRESS");
        }
      }
      String payload = winner.body().contains("CREDIT") ? changed : fixture.body();
      var replay = post(http, fixture, fixture.bearer(), payload, key);
      assertThat(replay.statusCode()).isEqualTo(201);
      assertThat(replay.body()).isEqualTo(winner.body());
      assertThat(countReceivables(fixture.company())).isEqualTo(payload.equals(changed) ? 1 : 0);
      CATALOG.verify(1, WireMock.getRequestedFor(WireMock.urlPathEqualTo(fixture.productPath())));
      assertThat(countSales(fixture.company())).isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  """
                  SELECT last_number FROM sales.sale_number_sequences WHERE company_id = ?
                  """,
                  Long.class,
                  fixture.company()))
          .isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  """
                  SELECT count(*) FROM sales.sale_lines l JOIN sales.sales s ON s.id = l.sale_id
                  WHERE s.company_id = ?
                  """,
                  Long.class,
                  fixture.company()))
          .isEqualTo(2);
    }
  }

  @Test
  @Tag("regression")
  void isolatesTheSameKeyAcrossActorsAndCompaniesAndReplaysFromAnotherInstance() throws Exception {
    String key = UUID.randomUUID().toString();
    var original = fixture();
    var sameTenant = fixture(UUID.randomUUID(), original.company(), "REGULAR");
    var differentTenant = fixture(original.user(), UUID.randomUUID(), "REGULAR");
    original.membership("OWNER", false);
    sameTenant.membership("OWNER", false);
    try (var http = HttpClient.newHttpClient()) {
      var first = post(http, original, original.bearer(), creditBody(original), key);
      assertThat(first.statusCode()).withFailMessage(first.body()).isEqualTo(201);
      var otherActor = post(http, sameTenant, sameTenant.bearer(), sameTenant.body(), key);
      assertThat(otherActor.statusCode()).isEqualTo(201);
      assertThat(otherActor.body()).contains("V-2026-002");

      differentTenant.membership("OWNER", false);
      var otherCompany =
          post(http, differentTenant, differentTenant.bearer(), differentTenant.body(), key);
      assertThat(otherCompany.statusCode()).isEqualTo(201);
      assertThat(otherCompany.body()).contains("V-2026-001").isNotEqualTo(first.body());

      original.membership("OWNER", false);
      try (var secondInstance =
          new SpringApplicationBuilder(SalesServiceApplication.class)
              .profiles("test")
              .run(
                  "--server.port=0",
                  "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                  "--spring.datasource.password=" + POSTGRES.getPassword(),
                  "--sanigest.jwt.public-key="
                      + environment.getRequiredProperty("sanigest.jwt.public-key"),
                  "--sanigest.company-access.enabled=true",
                  "--sanigest.company-access.origin=" + CATALOG.baseUrl(),
                  "--sanigest.company-access.private-key-location="
                      + environment.getRequiredProperty(
                          "sanigest.company-access.private-key-location"))) {
        var response =
            post(
                http,
                original,
                original.bearer(),
                creditBody(original),
                key,
                secondInstance
                    .getEnvironment()
                    .getRequiredProperty("local.server.port", Integer.class));
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body()).isEqualTo(first.body());
        var id = UUID.fromString(new ObjectMapper().readTree(first.body()).path("id").asString());
        var stored =
            secondInstance
                .getBean(
                    com.sanigest.sales.application.port.out.persistence.ReceivableRepository.class)
                .findByCompanyIdAndId(original.company(), id)
                .orElseThrow();
        assertThat(stored.status().name()).isEqualTo("OPEN");
        assertThat(stored.balance()).isEqualTo(new BigDecimal("12601.50"));
        CATALOG.verify(
            1, WireMock.getRequestedFor(WireMock.urlPathEqualTo(original.productPath())));
      }
    }
    assertThat(countSales(original.company())).isEqualTo(2);
    assertThat(countSales(differentTenant.company())).isEqualTo(1);
  }

  @Test
  @Tag("regression")
  void translatesAnUncommittedClaimLockTimeoutAndAllowsRetryAfterRollback() throws Exception {
    var fixture = fixture();
    fixture.membership("OWNER", false);
    String key = UUID.randomUUID().toString();
    try (var http = HttpClient.newHttpClient();
        var connection =
            new DriverManagerDataSource(
                    POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .getConnection()) {
      connection.setAutoCommit(false);
      try (var insert =
          connection.prepareStatement(
              """
              INSERT INTO sales.sale_idempotency_keys
                  (company_id, actor_id, operation, idempotency_key, fingerprint_version, request_hash,
                   claim_token, created_at, expires_at)
              VALUES (?, ?, 'POST /api/v1/sales', ?, 1, repeat('a', 64), ?, now(), now() + interval '1 minute')
              """)) {
        insert.setObject(1, fixture.company());
        insert.setObject(2, fixture.user());
        insert.setString(3, key);
        insert.setObject(4, UUID.randomUUID());
        insert.executeUpdate();
        var busy = post(http, fixture, fixture.bearer(), fixture.body(), key);
        assertThat(busy.statusCode()).withFailMessage(busy.body()).isEqualTo(409);
        assertThat(busy.body()).contains("IDEMPOTENCY_IN_PROGRESS");
        assertThat(busy.headers().firstValue("Retry-After")).contains("1");
        assertThat(countSales(fixture.company())).isZero();
        connection.rollback();
      }
      assertThat(post(http, fixture, fixture.bearer(), fixture.body(), key).statusCode())
          .isEqualTo(201);
    }
  }

  @Test
  @Tag("regression")
  void reclaimsAnAbandonedLeaseAndFencesTheOriginalWorker() {
    var request =
        new Request(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "POST /api/v1/sales",
            UUID.randomUUID().toString(),
            1,
            "a".repeat(64));
    var abandoned = (Claim) idempotency.resolve(request);
    assertThatThrownBy(() -> idempotency.resolve(request))
        .isInstanceOf(IdempotencyInProgress.class);
    jdbc.update(
        """
        UPDATE sales.sale_idempotency_keys
        SET created_at = now() - interval '2 minutes', expires_at = now() - interval '1 minute'
        WHERE company_id = ?
        """,
        request.companyId());
    var replacement = (Claim) idempotency.resolve(request);
    assertThat(replacement.token()).isNotEqualTo(abandoned.token());
    assertThatThrownBy(() ->
            idempotency.complete(
                request,
                abandoned,
                () -> {
                  throw new AssertionError(
                      "An abandoned claim must never enter the sale transaction");
                }))
        .isInstanceOf(IdempotencyInProgress.class);
    idempotency.release(request, abandoned);
    assertThatThrownBy(() -> idempotency.resolve(request))
        .isInstanceOf(IdempotencyInProgress.class);
    idempotency.release(request, replacement);
    assertThat(idempotency.resolve(request)).isInstanceOf(Claim.class);
  }

  @Test
  @Tag("regression")
  void rollsBackSaleAndCounterWhenTheIdempotencyResultCannotBeWritten() throws Exception {
    var fixture = fixture();
    fixture.membership("OWNER", false);
    String key = UUID.randomUUID().toString();
    jdbc.execute(
        """
        ALTER TABLE sales.sale_idempotency_keys ADD CONSTRAINT reject_test_response
        CHECK (idempotency_key <> '%s' OR response_body IS NULL)
        """
            .formatted(key));
    try (var http = HttpClient.newHttpClient()) {
      try {
        var failed = post(http, fixture, fixture.bearer(), creditBody(fixture), key);
        assertThat(failed.statusCode()).isEqualTo(409);
        assertThat(failed.body()).contains("SALE_CONFLICT");
        assertThat(countSales(fixture.company())).isZero();
        assertThat(countReceivables(fixture.company())).isZero();
        assertThat(
                jdbc.queryForObject(
                    """
                    SELECT count(*) FROM sales.sale_number_sequences WHERE company_id = ?
                    """,
                    Long.class,
                    fixture.company()))
            .isZero();
        assertThat(
                jdbc.queryForObject(
                    """
                    SELECT count(*) FROM sales.sale_idempotency_keys WHERE company_id = ?
                    """,
                    Long.class,
                    fixture.company()))
            .isZero();
      } finally {
        jdbc.execute(
            "ALTER TABLE sales.sale_idempotency_keys DROP CONSTRAINT reject_test_response");
      }
      var recovered = post(http, fixture, fixture.bearer(), creditBody(fixture), key);
      assertThat(recovered.statusCode()).isEqualTo(201);
      assertThat(recovered.body()).contains("V-2026-001");
    }
  }

  @Test
  @Tag("smoke")
  void listsARegisteredSaleWithReadPermission() throws Exception {
    var fixture = fixture();
    fixture.membership("OWNER", false);
    try (var http = HttpClient.newHttpClient()) {
      var response = post(http, fixture, fixture.bearer(), fixture.body());
      assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(201);
      var body = new ObjectMapper().readTree(response.body());

      fixture.membership("VIEWER", false, "READ");
      var listed = page(getSales(http, fixture.bearer(), ""));
      assertThat(listed.path("content").size()).isEqualTo(1);
      var summary = listed.path("content").get(0);
      for (String field :
          List.of("id", "code", "client", "saleDate", "mode", "status", "total", "currency")) {
        assertThat(summary.path(field)).isEqualTo(body.path(field));
      }
      assertThat(summary.has("lines")).isFalse();
      assertThat(listed.path("page").asInt()).isZero();
      assertThat(listed.path("size").asInt()).isEqualTo(20);
      assertThat(listed.path("totalElements").asLong()).isEqualTo(1);
      assertThat(listed.path("totalPages").asLong()).isEqualTo(1);
    }
  }

  @Test
  @Tag("regression")
  void listsStablePagesWithinTheAuthorizedCompanyAndFiltersLiteralSnapshots() throws Exception {
    var fixture = fixture();
    var other = fixture();
    fixture.membership("OPERATOR", false, "READ");
    other.membership("VIEWER", false, "READ");
    var first = UUID.fromString("00000000-0000-0000-0000-000000000003");
    var second = UUID.fromString("00000000-0000-0000-0000-000000000002");
    var third = UUID.fromString("00000000-0000-0000-0000-000000000001");
    insertListedSale(fixture, first, "Acme %_! Cliente", "2026-11-23", "REGISTERED", "V-2026-003");
    insertListedSale(fixture, second, "Acme %_! Cliente", "2026-11-23", "CANCELLED", "V-2026-002");
    insertListedSale(fixture, third, "Acme %_! Cliente", "2026-11-22", "REGISTERED", "V-2026-001");
    insertListedSale(
        fixture, UUID.randomUUID(), "Acme AB! Cliente", "2026-11-23", "REGISTERED", null);
    insertListedSale(
        fixture, UUID.randomUUID(), "Acme A_! Cliente", "2026-11-23", "REGISTERED", null);
    insertListedSale(
        fixture, UUID.randomUUID(), "Acme %B! Cliente", "2026-11-23", "REGISTERED", null);
    insertListedSale(
        fixture, UUID.randomUUID(), "Acme %_! Cliente", "2026-11-24", "REGISTERED", null);
    insertListedSale(
        other, UUID.randomUUID(), "Acme %_! Cliente", "2026-11-23", "REGISTERED", "V-2026-003");

    var stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    stats.setStatisticsEnabled(true);
    stats.clear();
    try (var http = HttpClient.newHttpClient()) {
      String filters =
          "?search=%20aCME%20%25_%21%20&from=2026-11-22&to=2026-11-23&size=2"
              + "&companyId="
              + other.company();
      var firstPage = page(getSales(http, fixture.bearer(), filters));
      assertThat(stats.getPrepareStatementCount()).isEqualTo(2);
      assertThat(stats.getEntityLoadCount()).isZero();
      assertThat(listIds(firstPage)).containsExactly(first.toString(), second.toString());
      assertThat(firstPage.path("totalElements").asLong()).isEqualTo(3);
      assertThat(firstPage.path("totalPages").asLong()).isEqualTo(2);
      var secondPage = page(getSales(http, fixture.bearer(), filters + "&page=1"));
      assertThat(listIds(secondPage)).containsExactly(third.toString());
      var emptyPage = page(getSales(http, fixture.bearer(), filters + "&page=2"));
      assertThat(listIds(emptyPage)).isEmpty();
      assertThat(emptyPage.path("totalElements").asLong()).isEqualTo(3);
      var registered = page(getSales(http, fixture.bearer(), filters + "&status=registered"));
      assertThat(listIds(registered)).containsExactly(first.toString(), third.toString());
      var cancelled = page(getSales(http, fixture.bearer(), filters + "&status=CANCELLED"));
      assertThat(listIds(cancelled)).containsExactly(second.toString());
      assertThat(cancelled.path("totalElements").asLong()).isEqualTo(1);
      assertThat(page(getSales(http, other.bearer(), filters)).path("totalElements").asLong())
          .isEqualTo(1);
      assertThat(
              page(getSales(http, fixture.bearer(), "?search=AB"))
                  .path("content")
                  .get(0)
                  .path("code")
                  .isNull())
          .isTrue();
      assertThat(countSales(fixture.company())).isEqualTo(7);
      CATALOG.verify(
          0,
          WireMock.getRequestedFor(WireMock.urlPathEqualTo("/api/v1/clients/" + fixture.client())));
    } finally {
      stats.setStatisticsEnabled(false);
    }
  }

  @Test
  @Tag("regression")
  void rejectsInvalidFiltersAndFailsClosedOnListing() throws Exception {
    var fixture = fixture();
    try (var http = HttpClient.newHttpClient()) {
      assertThat(getSales(http, null, "").statusCode()).isEqualTo(401);
      assertThat(getSales(http, "invalid", "").statusCode()).isEqualTo(401);
      fixture.membership("VIEWER", false, "READ");
      for (String query :
          List.of(
              "?status=PAID",
              "?from=2026-02-29",
              "?to=0000-01-01",
              "?from=10000-01-01",
              "?from=2026-11-23&to=2026-11-22",
              "?page=bad",
              "?page=-1",
              "?page=2147483647",
              "?size=0",
              "?size=101")) {
        var response = getSales(http, fixture.bearer(), query);
        assertThat(response.statusCode()).as(query).isEqualTo(400);
        assertThat(response.body()).contains("INVALID_REQUEST");
      }
      var empty = page(getSales(http, fixture.bearer(), "?from=2026-11-23"));
      assertThat(listIds(empty)).isEmpty();
      assertThat(empty.path("totalPages").asLong()).isZero();
      fixture.companyReply(403);
      assertThat(getSales(http, fixture.bearer(), "").statusCode()).isEqualTo(403);
      fixture.companyReply(503);
      var unavailable = getSales(http, fixture.bearer(), "");
      assertThat(unavailable.statusCode()).isEqualTo(503);
      assertThat(unavailable.body()).contains("AUTHORIZATION_UNAVAILABLE");
    }
  }

  @Test
  @Tag("smoke")
  void readsHistoricalDetailCancelsAndReplaysThroughHttp() throws Exception {
    var fixture = fixture();
    fixture.membership("OWNER", false);
    try (var http = HttpClient.newHttpClient()) {
      String registrationKey = UUID.randomUUID().toString();
      var creation = post(http, fixture, fixture.bearer(), fixture.body(), registrationKey);
      assertThat(creation.statusCode()).withFailMessage(creation.body()).isEqualTo(201);
      var registered = new ObjectMapper().readTree(creation.body());
      String id = registered.path("id").asString();
      fixture.item(fixture.productPath(), fixture.product(), "PRODUCT", "9999.00", false);
      fixture.customer("INACTIVE");
      fixture.membership("VIEWER", false, "READ");

      assertThat(page(getSales(http, fixture.bearer(), "/" + id))).isEqualTo(registered);
      assertThat(registered.has("payments")).isFalse();
      assertThat(registered.path("lines").size()).isEqualTo(2);
      assertThat(registered.path("total").asString()).isEqualTo("12601.50");

      fixture.membership("OPERATOR", true);
      assertThat(cancel(http, fixture.bearer(), id, "{\"reason\":\"Duplicada\"}").statusCode())
          .isEqualTo(403);

      fixture.membership("OWNER", false);
      var cancelled = page(cancel(http, fixture.bearer(), id, "{\"reason\":\"  Duplicada  \"}"));
      assertThat(cancelled.path("status").asString()).isEqualTo("CANCELLED");
      assertThat(cancelled.path("cancellation").path("cancelledBy").asString())
          .isEqualTo(fixture.user().toString());
      assertThat(cancelled.path("cancellation").path("cancelReason").asString())
          .isEqualTo("Duplicada");
      Instant cancelledAt =
          Instant.parse(cancelled.path("cancellation").path("cancelledAt").asString());
      for (String field :
          List.of("id", "code", "client", "lines", "total", "currency", "mode", "saleDate")) {
        assertThat(cancelled.path(field)).isEqualTo(registered.path(field));
      }

      var admin = fixture(UUID.randomUUID(), fixture.company(), "REGULAR");
      admin.membership("ADMIN", false);
      assertThat(page(cancel(http, admin.bearer(), id, "{\"reason\":\"Otro motivo\"}")))
          .isEqualTo(cancelled);

      fixture.membership("VIEWER", false, "READ");
      assertThat(page(getSales(http, fixture.bearer(), "/" + id))).isEqualTo(cancelled);
      assertThat(listIds(page(getSales(http, fixture.bearer(), "?status=CANCELLED"))))
          .containsExactly(id);
      assertThat(
              sales
                  .findByCompanyIdAndId(fixture.company(), UUID.fromString(id))
                  .orElseThrow()
                  .cancellation()
                  .cancelledAt())
          .isEqualTo(cancelledAt);
      fixture.membership("OWNER", false);
      var replay = post(http, fixture, fixture.bearer(), fixture.body(), registrationKey);
      assertThat(replay.statusCode()).isEqualTo(201);
      assertThat(replay.body()).isEqualTo(creation.body());
      assertThat(countSales(fixture.company())).isEqualTo(1);
      CATALOG.verify(1, WireMock.getRequestedFor(WireMock.urlPathEqualTo(fixture.productPath())));
    }
  }

  @Test
  @Tag("regression")
  void serializesTwoConcurrentCancellationsAndKeepsTheWinner() throws Exception {
    var owner = fixture();
    var admin = fixture(UUID.randomUUID(), owner.company(), "REGULAR");
    owner.membership("OWNER", false);
    admin.membership("ADMIN", false);
    try (var http = HttpClient.newHttpClient();
        var executor = Executors.newFixedThreadPool(2)) {
      String id = registerCredit(http, owner);
      var start = new CyclicBarrier(2);
      var first =
          executor.submit(() -> {
            start.await(5, TimeUnit.SECONDS);
            return cancel(http, owner.bearer(), id, "{\"reason\":\"Primera\"}");
          });
      var second =
          executor.submit(() -> {
            start.await(5, TimeUnit.SECONDS);
            return cancel(http, admin.bearer(), id, "{\"reason\":\"Segunda\"}");
          });
      var result = page(first.get(10, TimeUnit.SECONDS));
      assertThat(page(second.get(10, TimeUnit.SECONDS))).isEqualTo(result);
      var details =
          sales
              .findByCompanyIdAndId(owner.company(), UUID.fromString(id))
              .orElseThrow()
              .cancellation();
      assertThat(details.cancelReason())
          .isEqualTo(details.cancelledBy().equals(owner.user()) ? "Primera" : "Segunda");
      assertThat(details.cancelledBy()).isIn(owner.user(), admin.user());
      assertThat(countSales(owner.company())).isEqualTo(1);
      owner.membership("OWNER", false, "READ");
      assertThat(page(getReceivable(http, owner.bearer(), id)).path("status").asString())
          .isEqualTo("CANCELLED");
    }
  }

  @Test
  @Tag("regression")
  void lifecycleRejectsInvalidInputAndHidesForeignSales() throws Exception {
    var owner = fixture();
    var other = fixture();
    var advisor = fixture("ADVISOR");
    owner.membership("OWNER", false);
    other.membership("ADMIN", false);
    advisor.companyReply(403);
    try (var http = HttpClient.newHttpClient()) {
      String id = registerForLifecycle(http, owner).path("id").asString();
      String otherId = registerForLifecycle(http, other).path("id").asString();
      String reason = "{\"reason\":\"Duplicada\"}";
      assertThat(cancel(http, null, id, reason).statusCode()).isEqualTo(401);
      assertThat(cancel(http, "invalid", id, reason).statusCode()).isEqualTo(401);
      assertThat(getSales(http, null, "/" + id).statusCode()).isEqualTo(401);
      assertThat(getSales(http, "invalid", "/" + id).statusCode()).isEqualTo(401);
      assertThat(cancel(http, advisor.bearer(), id, reason).statusCode()).isEqualTo(403);

      var foreign = cancel(http, other.bearer(), id, reason);
      assertThat(foreign.statusCode()).isEqualTo(404);
      assertThat(foreign.body()).contains("SALE_NOT_FOUND");
      assertThat(cancel(http, other.bearer(), UUID.randomUUID().toString(), reason).statusCode())
          .isEqualTo(404);
      assertThat(cancel(http, owner.bearer(), "1-1-1-1-1", reason).statusCode()).isEqualTo(400);
      for (String invalid :
          List.of(
              "{}",
              "{\"reason\":null}",
              "{\"reason\":17}",
              "{\"reason\":\"  \"}",
              "{\"reason\":\"x\",\"actorId\":\"forged\"}",
              "{\"reason\":\"x\",\"companyId\":\"forged\"}",
              "{\"reason\":\"x\",\"status\":\"REGISTERED\"}",
              "{\"reason\":\"x\",\"cancelledAt\":\"2026-11-23T12:00:00Z\"}",
              "{\"reason\":\"" + "x".repeat(501) + "\"}")) {
        var response = cancel(http, owner.bearer(), id, invalid);
        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(400);
        assertThat(response.body()).contains("INVALID_REQUEST");
      }
      owner.companyReply(503);
      assertThat(cancel(http, owner.bearer(), id, reason).statusCode()).isEqualTo(503);

      other.membership("VIEWER", false, "READ");
      assertThat(
              getSales(http, other.bearer(), "/" + id + "?companyId=" + owner.company())
                  .statusCode())
          .isEqualTo(404);
      assertThat(getSales(http, other.bearer(), "/" + UUID.randomUUID()).statusCode())
          .isEqualTo(404);
      assertThat(getSales(http, other.bearer(), "/invalid").statusCode()).isEqualTo(400);
      assertThat(page(getSales(http, other.bearer(), "/" + otherId)).path("status").asString())
          .isEqualTo("REGISTERED");
      assertThat(getSales(http, owner.bearer(), "/" + id).statusCode()).isEqualTo(503);
      assertThat(
              sales
                  .findByCompanyIdAndId(owner.company(), UUID.fromString(id))
                  .orElseThrow()
                  .status())
          .isEqualTo(SaleStatus.REGISTERED);
    }
  }

  @Test
  @Tag("regression")
  void rejectsAnUpdateThatAffectsNoRowsAndLeavesTheSaleRegistered() throws Exception {
    var owner = fixture();
    owner.membership("OWNER", false);
    try (var http = HttpClient.newHttpClient()) {
      String id = registerCredit(http, owner);
      jdbc.execute(
          """
          CREATE FUNCTION sales.test_skip_sale_update() RETURNS trigger LANGUAGE plpgsql AS
          $$ BEGIN RETURN NULL; END; $$
          """);
      try {
        jdbc.execute(
            """
            CREATE TRIGGER test_skip_sale_update BEFORE UPDATE ON sales.sales
            FOR EACH ROW WHEN (OLD.id = '%s') EXECUTE FUNCTION sales.test_skip_sale_update()
            """
                .formatted(id));
        var response = cancel(http, owner.bearer(), id, "{\"reason\":\"Duplicada\"}");
        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(409);
        assertThat(response.body()).contains("SALE_CANCELLATION_CONFLICT");

        var unchanged =
            sales.findByCompanyIdAndId(owner.company(), UUID.fromString(id)).orElseThrow();
        assertThat(unchanged.status()).isEqualTo(SaleStatus.REGISTERED);
        assertThat(unchanged.cancellation()).isNull();
        owner.membership("OWNER", false, "READ");
        assertThat(page(getReceivable(http, owner.bearer(), id)).path("status").asString())
            .isEqualTo("OPEN");
      } finally {
        jdbc.execute("DROP TRIGGER IF EXISTS test_skip_sale_update ON sales.sales");
        jdbc.execute("DROP FUNCTION sales.test_skip_sale_update()");
      }
    }
  }

  @Test
  @Tag("regression")
  void replaysCompletedLegacyCreditWithoutInventingADueDateOrCallingCatalog() throws Exception {
    var owner = fixture();
    owner.membership("OWNER", false);
    var saleId = UUID.randomUUID();
    var key = UUID.randomUUID().toString();
    var payload = owner.body().replace("CASH", "CREDIT");
    var response = "{\"id\":\"%s\",\"mode\":\"CREDIT\"}".formatted(saleId);
    jdbc.update(
        """
        INSERT INTO sales.sales (id, company_id, client_id, client_name, sale_date, mode, status, total)
        VALUES (?, ?, ?, 'Cliente', DATE '2026-11-23', 'CREDIT', 'REGISTERED', 12601.50)
        """,
        saleId,
        owner.company(),
        owner.client());
    var canonical =
        String.join(
            "\n",
            "POST /api/v1/sales",
            "1",
            owner.client().toString(),
            "CREDIT",
            "2026-11-23",
            owner.product().toString(),
            "2",
            owner.service().toString(),
            "3");
    var hash =
        java.util.HexFormat.of()
            .formatHex(
                java.security.MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    jdbc.update(
        """
        INSERT INTO sales.sale_idempotency_keys
            (company_id, actor_id, operation, idempotency_key, fingerprint_version, request_hash,
             sale_id, response_body, response_status, response_version, created_at, completed_at, expires_at)
        VALUES (?, ?, 'POST /api/v1/sales', ?, 1, ?, ?, ?, 201, 1,
                now() - interval '3 days', now() - interval '2 days', now() - interval '1 day')
        """,
        owner.company(),
        owner.user(),
        key,
        hash,
        saleId,
        response);
    try (var http = HttpClient.newHttpClient()) {
      var replay = post(http, owner, owner.bearer(), payload, key);
      assertThat(replay.statusCode()).isEqualTo(201);
      assertThat(replay.body()).isEqualTo(response);
      assertThat(post(http, owner, owner.bearer(), creditBody(owner), key).statusCode())
          .isEqualTo(409);
      assertThat(countReceivables(owner.company())).isZero();
      assertThat(countSales(owner.company())).isEqualTo(1);
      CATALOG.verify(0, WireMock.getRequestedFor(WireMock.urlPathEqualTo(owner.productPath())));
    }
  }

  @Test
  @Tag("smoke")
  void creditCreatesOneReceivableAndCancellationPreservesTheOriginalAmount() throws Exception {
    var owner = fixture();
    owner.membership("OWNER", false);
    var other = fixture();
    other.membership("OWNER", false);
    try (var http = HttpClient.newHttpClient()) {
      var key = UUID.randomUUID().toString();
      var payload = creditBody(owner);
      var created = post(http, owner, owner.bearer(), payload, key);
      assertThat(created.statusCode()).withFailMessage(created.body()).isEqualTo(201);
      var sale = new ObjectMapper().readTree(created.body());
      String id = sale.path("id").asString();
      owner.membership("OWNER", false, "READ");
      var receivable = page(getReceivable(http, owner.bearer(), id));
      assertThat(receivable.path("saleId").asString()).isEqualTo(id);
      assertThat(receivable.path("origin").asString()).isEqualTo("SALE");
      assertThat(receivable.path("currency").asString()).isEqualTo("DOP");
      assertThat(receivable.path("dueDate").asString()).isEqualTo("2026-12-23");
      assertThat(receivable.path("client")).isEqualTo(sale.path("client"));
      assertThat(receivable.path("balance")).isEqualTo(sale.path("total"));
      assertThat(receivable.path("totalAmount")).isEqualTo(sale.path("total"));
      assertThat(receivable.path("status").asString()).isEqualTo("OPEN");

      owner.membership("OWNER", false);
      jdbc.update(
          """
          UPDATE sales.sale_idempotency_keys
          SET completed_at = now() - interval '1 minute', created_at = now() - interval '1 minute',
              expires_at = now() - interval '1 second'
          WHERE sale_id = ?
          """,
          UUID.fromString(id));
      var replay = post(http, owner, owner.bearer(), payload, key);
      assertThat(replay.statusCode()).isEqualTo(201);
      assertThat(replay.body()).isEqualTo(created.body());
      assertThat(
              post(http, owner, owner.bearer(), payload.replace("12-23", "12-24"), key)
                  .statusCode())
          .isEqualTo(409);
      assertThat(countReceivables(owner.company())).isEqualTo(1);
      owner.membership("OWNER", false, "READ");
      other.membership("OWNER", false, "READ");
      assertThat(getReceivable(http, null, id).statusCode()).isEqualTo(401);
      assertThat(getReceivable(http, "invalid", id).statusCode()).isEqualTo(401);
      assertThat(getReceivable(http, other.bearer(), id).statusCode()).isEqualTo(404);
      assertThat(getReceivable(http, owner.bearer(), UUID.randomUUID().toString()).statusCode())
          .isEqualTo(404);
      other.companyReply(403);
      assertThat(getReceivable(http, other.bearer(), id).statusCode()).isEqualTo(403);

      owner.membership("OWNER", false);
      var cancelled = cancel(http, owner.bearer(), id, "{\"reason\":\"Duplicada\"}");
      assertThat(page(cancelled).path("status").asString()).isEqualTo("CANCELLED");
      owner.membership("OWNER", false, "READ");
      var stored = page(getReceivable(http, owner.bearer(), id));
      assertThat(stored.path("status").asString()).isEqualTo("CANCELLED");
      assertThat(stored.path("totalAmount")).isEqualTo(receivable.path("totalAmount"));
      assertThat(stored.path("balance")).isEqualTo(receivable.path("balance"));
      assertThat(countReceivables(owner.company())).isEqualTo(1);
    }
  }

  @Test
  @Tag("regression")
  void invalidDueDatesAndInsertFailureLeaveNoCreditSaleAndAllowRetry() throws Exception {
    var owner = fixture();
    owner.membership("OWNER", false);
    try (var http = HttpClient.newHttpClient()) {
      String payload = creditBody(owner);
      for (String invalid :
          List.of(
              owner.body().replace("CASH", "CREDIT"),
              payload.replace("2026-12-23", "2026-11-22"),
              payload.replace("2026-12-23", "2026-02-30"),
              payload.replace("2026-12-23", "2026-12-23T00:00:00"))) {
        assertThat(post(http, owner, owner.bearer(), invalid).statusCode()).isEqualTo(400);
      }
      String key = UUID.randomUUID().toString();
      jdbc.execute(
          """
          ALTER TABLE sales.sales ADD CONSTRAINT reject_test_credit_sale
          CHECK (company_id <> '%s')
          """
              .formatted(owner.company()));
      try {
        assertThat(post(http, owner, owner.bearer(), payload, key).statusCode()).isEqualTo(409);
        assertThat(countSales(owner.company())).isZero();
        assertThat(countReceivables(owner.company())).isZero();
      } finally {
        jdbc.execute("ALTER TABLE sales.sales DROP CONSTRAINT reject_test_credit_sale");
      }
      jdbc.execute(
          """
          ALTER TABLE sales.receivables ADD CONSTRAINT reject_test_receivable
          CHECK (company_id <> '%s')
          """
              .formatted(owner.company()));
      try {
        assertThat(post(http, owner, owner.bearer(), payload, key).statusCode()).isEqualTo(409);
        assertThat(countSales(owner.company())).isZero();
        assertThat(countReceivables(owner.company())).isZero();
      } finally {
        jdbc.execute("ALTER TABLE sales.receivables DROP CONSTRAINT reject_test_receivable");
      }
      var retry = post(http, owner, owner.bearer(), payload, key);
      assertThat(retry.statusCode()).withFailMessage(retry.body()).isEqualTo(201);
      assertThat(retry.body()).contains("V-2026-001");
      assertThat(countReceivables(owner.company())).isEqualTo(1);
      assertThatThrownBy(() -> copyReceivable(owner.company(), owner.company()))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("receivables_sale_id_key");

      assertThatThrownBy(() ->
              jdbc.update(
                  "UPDATE sales.receivables SET company_id = ? WHERE company_id = ?",
                  UUID.randomUUID(),
                  owner.company()))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("receivables_sale_fk");
    }
  }

  private void copyReceivable(UUID sourceCompany, UUID targetCompany) {
    jdbc.update(
        """
        INSERT INTO sales.receivables
            (id, company_id, sale_id, sale_date, client_id, client_name, total_amount,
             balance, due_date, status, created_at, origin)
        SELECT ?, ?, sale_id, sale_date, client_id, client_name, total_amount, balance,
               due_date, status, created_at, origin
          FROM sales.receivables WHERE company_id = ?
        """,
        UUID.randomUUID(),
        targetCompany,
        sourceCompany);
  }

  private long countReceivables(UUID company) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM sales.receivables WHERE company_id = ?", Long.class, company);
  }

  private String registerCredit(HttpClient http, Fixture fixture) throws Exception {
    var response = post(http, fixture, fixture.bearer(), creditBody(fixture));
    assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(201);
    return new ObjectMapper().readTree(response.body()).path("id").asString();
  }

  private static String creditBody(Fixture fixture) {
    return fixture.body().replace("\"CASH\"", "\"CREDIT\",\"dueDate\":\"2026-12-23\"");
  }

  private HttpResponse<String> getReceivable(HttpClient http, String bearer, String id)
      throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/receivables/" + id))
            .timeout(Duration.ofSeconds(5))
            .GET();
    if (bearer != null) {
      request.header("Authorization", "Bearer " + bearer);
    }
    return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private JsonNode registerForLifecycle(HttpClient http, Fixture fixture) throws Exception {
    var response = post(http, fixture, fixture.bearer(), fixture.body());
    assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(201);
    return new ObjectMapper().readTree(response.body());
  }

  private HttpResponse<String> cancel(HttpClient http, String bearer, String id, String body)
      throws Exception {
    var request =
        HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/v1/sales/" + id + "/cancel"))
            .timeout(Duration.ofSeconds(5))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));
    if (bearer != null) {
      request.header("Authorization", "Bearer " + bearer);
    }
    return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private void insertListedSale(
      Fixture fixture, UUID id, String name, String date, String status, String code) {
    jdbc.update(
        """
        INSERT INTO sales.sales (id, company_id, client_id, client_name, sale_date, mode,
          status, total, code, cancelled_by, cancelled_at, cancel_reason)
        VALUES (?, ?, ?, ?, ?, 'CREDIT', ?, 123.40, ?, ?, ?, ?)
        """,
        id,
        fixture.company(),
        fixture.client(),
        name,
        LocalDate.parse(date),
        status,
        code,
        "CANCELLED".equals(status) ? fixture.user() : null,
        "CANCELLED".equals(status) ? OffsetDateTime.parse("2026-11-23T12:00:00Z") : null,
        "CANCELLED".equals(status) ? "Duplicada" : null);
  }

  private HttpResponse<String> getSales(HttpClient http, String bearer, String query)
      throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/sales" + query))
            .timeout(Duration.ofSeconds(5))
            .GET();
    if (bearer != null) {
      request.header("Authorization", "Bearer " + bearer);
    }
    return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private static JsonNode page(HttpResponse<String> response) {
    assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(200);
    return new ObjectMapper().readTree(response.body());
  }

  private static List<String> listIds(JsonNode page) {
    return page.path("content").valueStream().map(row -> row.path("id").asString()).toList();
  }

  private long countSales(UUID company) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM sales.sales WHERE company_id = ?", Long.class, company);
  }

  private HttpResponse<String> post(HttpClient http, Fixture fixture, String bearer, String body)
      throws Exception {
    return post(http, fixture, bearer, body, UUID.randomUUID().toString());
  }

  private HttpResponse<String> post(
      HttpClient http, Fixture fixture, String bearer, String body, String key) throws Exception {
    return post(http, fixture, bearer, body, key, port);
  }

  private HttpResponse<String> post(
      HttpClient http, Fixture fixture, String bearer, String body, String key, int targetPort)
      throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + targetPort + "/api/v1/sales"))
            .header("Content-Type", "application/json")
            .header("X-Company-Id", fixture.user().toString())
            .timeout(Duration.ofSeconds(10))
            .POST(HttpRequest.BodyPublishers.ofString(body));
    if (bearer != null) {
      request.header("Authorization", "Bearer " + bearer);
    }
    if (key != null) {
      request.header("Idempotency-Key", key);
    }
    return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private static Fixture fixture() throws Exception {
    return fixture("REGULAR");
  }

  private static Fixture fixture(String accountType) throws Exception {
    return fixture(UUID.randomUUID(), UUID.randomUUID(), accountType);
  }

  private static Fixture fixture(UUID user, UUID company, String accountType) throws Exception {
    var claims =
        new JWTClaimsSet.Builder()
            .issuer("sanigest-test")
            .audience("sanigest-api-test")
            .subject(user.toString())
            .jwtID(UUID.randomUUID().toString())
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
            company,
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
      membership(role, grant, "WRITE");
    }

    void membership(String role, boolean grant, String scope) {
      CATALOG.stubFor(
          WireMock.get(PATH)
              .withHeader(USER_ASSERTION, equalTo(bearer))
              .willReturn(
                  okJson(
                      """
                      {"userId":"%s","companyId":"%s","module":"SALES","scope":"%s",
                      "mode":"MEMBERSHIP","role":"%s","explicitGrant":%s}
                      """
                          .formatted(user, company, scope, role, grant))));
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
        null,
        null);
  }
}
