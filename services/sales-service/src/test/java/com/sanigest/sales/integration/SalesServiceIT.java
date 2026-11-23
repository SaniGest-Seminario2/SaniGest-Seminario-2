package com.sanigest.sales.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
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

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("SALES_DB_HOST", POSTGRES::getHost);
    registry.add("SALES_DB_PORT", () -> POSTGRES.getMappedPort(5432));
    registry.add("SALES_DB_PASSWORD", POSTGRES::getPassword);
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
    assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");

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
        .containsExactlyInAnyOrder("sales", "sale_lines");

    assertThat(
            jdbc.queryForList(
                """
                SELECT table_name || '.' || column_name || ':' || data_type || ':' || is_nullable
                FROM information_schema.columns WHERE table_schema = 'sales'
                """,
                String.class))
        .containsExactlyInAnyOrder(
            "sales.id:uuid:NO",
            "sales.company_id:uuid:NO",
            "sales.client_id:uuid:NO",
            "sales.client_name:character varying:NO",
            "sales.sale_date:date:NO",
            "sales.mode:character varying:NO",
            "sales.status:character varying:NO",
            "sales.total:numeric:NO",
            "sale_lines.id:uuid:NO",
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
            "sales_pkey", "sale_lines_pkey", "sales_company_date_id_idx", "sale_lines_sale_id_idx");
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
    assertThat(history).hasSize(1);
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
            List.of(first, second));

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
            List.of(updatedSnapshot)));

    Sale saved = sales.findByCompanyIdAndId(companyId, saleId).orElseThrow();
    assertThat(saved).usingRecursiveComparison().ignoringCollectionOrder().isEqualTo(original);
    assertThat(saved.total()).isEqualTo(new BigDecimal("15.01"));
    assertThat(sales.findByCompanyIdAndId(UUID.randomUUID(), saleId)).isEmpty();
  }

  @Test
  @Tag("regression")
  void rollsBackTheEntireSaleWhenALineCannotBeInserted() {
    UUID lineId = UUID.randomUUID();
    Sale existing = singleLineSale(lineId);
    Sale conflicting = singleLineSale(lineId);
    sales.insert(existing);

    assertThatThrownBy(() -> sales.insert(conflicting))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(sales.findByCompanyIdAndId(conflicting.companyId(), conflicting.id())).isEmpty();
    assertThat(sales.findByCompanyIdAndId(existing.companyId(), existing.id())).contains(existing);
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

  private Sale singleLineSale(UUID lineId) {
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
        UUID.randomUUID(),
        UUID.randomUUID(),
        "Cliente",
        LocalDate.of(2026, 11, 23),
        SaleMode.CASH,
        SaleStatus.REGISTERED,
        BigDecimal.ONE,
        List.of(line));
  }
}
