package com.sanigest.catalog.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.sanigest.companyaccess.CompanyAccessProtocol.PATH;
import static com.sanigest.companyaccess.CompanyAccessProtocol.USER_ASSERTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.MatchResult;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sanigest.catalog.CatalogServiceApplication;
import com.sanigest.catalog.api.dto.ClientPatchRequest;
import com.sanigest.catalog.application.dto.ClientSearchCriteria;
import com.sanigest.catalog.application.exception.CatalogItemAccessException;
import com.sanigest.catalog.application.exception.ClientAccessException;
import com.sanigest.catalog.application.exception.ClientCreationException;
import com.sanigest.catalog.application.exception.SupplierAccessException;
import com.sanigest.catalog.application.port.out.persistence.CatalogItemCommercialRepository;
import com.sanigest.catalog.application.port.out.persistence.CatalogItemRepository;
import com.sanigest.catalog.application.port.out.persistence.ClientCategoryRepository;
import com.sanigest.catalog.application.port.out.persistence.ClientRepository;
import com.sanigest.catalog.application.port.out.persistence.SupplierRepository;
import com.sanigest.catalog.application.usecase.catalogitem.ChangeCatalogItemStatus;
import com.sanigest.catalog.application.usecase.catalogitem.CreateCatalogItem;
import com.sanigest.catalog.application.usecase.catalogitem.GetCatalogItem;
import com.sanigest.catalog.application.usecase.catalogitem.ListCatalogItems;
import com.sanigest.catalog.application.usecase.catalogitem.UpdateCatalogItem;
import com.sanigest.catalog.application.usecase.client.CreateClient;
import com.sanigest.catalog.application.usecase.client.ListClients;
import com.sanigest.catalog.application.usecase.supplier.ChangeSupplierStatus;
import com.sanigest.catalog.application.usecase.supplier.CreateSupplier;
import com.sanigest.catalog.application.usecase.supplier.GetSupplier;
import com.sanigest.catalog.application.usecase.supplier.ListSuppliers;
import com.sanigest.catalog.application.usecase.supplier.UpdateSupplier;
import com.sanigest.catalog.domain.enums.CatalogItemType;
import com.sanigest.catalog.domain.enums.ClientPaymentTerms;
import com.sanigest.catalog.domain.enums.ClientStatus;
import com.sanigest.catalog.domain.enums.ClientType;
import com.sanigest.catalog.domain.enums.SupplierPaymentTerms;
import com.sanigest.catalog.domain.enums.SupplierStatus;
import com.sanigest.catalog.domain.enums.SupplierSupplyType;
import com.sanigest.catalog.domain.model.CatalogItem;
import com.sanigest.catalog.domain.model.CatalogItemPatch;
import com.sanigest.catalog.domain.model.CatalogItemProfile;
import com.sanigest.catalog.domain.model.Client;
import com.sanigest.catalog.domain.model.ClientDetails;
import com.sanigest.catalog.domain.model.ClientProfile;
import com.sanigest.catalog.domain.model.Supplier;
import com.sanigest.catalog.domain.model.SupplierDetails;
import com.sanigest.catalog.domain.model.SupplierPatch;
import com.sanigest.catalog.domain.model.SupplierProfile;
import com.sanigest.companyaccess.CompanyAccess.MembershipRole;
import com.sanigest.companyaccess.CompanyAccess.Scope;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import java.io.IOException;
import java.io.UncheckedIOException;
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
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"local", "test"})
@Testcontainers
class CatalogServiceIT {
  private static final String CATALOG = "sanigest_catalog";
  private static final String ISSUER = "sanigest-test";
  private static final String AUDIENCE = "sanigest-api-test";
  private static final Map<String, String> PASSWORDS =
      Map.of(
          "IDENTITY_DB_PASSWORD", UUID.randomUUID().toString(),
          "COMPANY_DB_PASSWORD", UUID.randomUUID().toString(),
          "CATALOG_DB_PASSWORD", UUID.randomUUID().toString(),
          "SALES_DB_PASSWORD", UUID.randomUUID().toString(),
          "INVENTORY_DB_PASSWORD", UUID.randomUUID().toString(),
          "EXPENSE_DB_PASSWORD", UUID.randomUUID().toString(),
          "FINANCE_DB_PASSWORD", UUID.randomUUID().toString());
  private static final KeyPair KEY = rsaKey();
  private static final Path PUBLIC_KEY = writeKey("PUBLIC KEY", KEY.getPublic().getEncoded());
  private static final Path COMPANY_ACCESS_KEY =
      writeKey("PRIVATE KEY", rsaKey().getPrivate().getEncoded());
  private static final WireMockServer COMPANY = new WireMockServer(0);

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17.7-alpine")
          .withDatabaseName("postgres")
          .withUsername("sanigest_admin")
          .withPassword(UUID.randomUUID().toString())
          .withEnv(PASSWORDS)
          .withCopyFileToContainer(
              MountableFile.forHostPath(
                  "../../infrastructure/postgres/init/10-create-service-databases.sh", 0755),
              "/docker-entrypoint-initdb.d/10-create-service-databases.sh");

  private static final String SUPPLIERS = "/api/v1/suppliers";
  private static final String SUPPLIER_JSON =
      """
      {"name":"Office","contact":"Laura"}
      """;

  private static final String CATALOG_ITEMS = "/api/v1/catalog-items";
  private static final String SERVICE_ITEM =
      """
      {"name":"Consultoría","type":"SERVICE","price":15000.00,"unitCode":"UNIT",
       "taxClassificationCode":"EXEMPT","availableForPurchase":true}
      """;
  private static final String PRODUCT_ITEM =
      """
      {"name":"Silla","type":"PRODUCT","price":3200.00,"unitCode":"UNIT",
       "taxClassificationCode":"EXEMPT","availableForPurchase":true}
      """;

  @Autowired CreateCatalogItem createCatalogItem;
  @Autowired CatalogItemRepository items;
  @Autowired CatalogItemCommercialRepository itemCommercial;
  @Autowired ListCatalogItems listCatalogItems;
  @Autowired GetCatalogItem getCatalogItem;
  @Autowired UpdateCatalogItem updateCatalogItem;
  @Autowired ChangeCatalogItemStatus changeCatalogItemStatus;
  @Autowired CreateSupplier createSupplier;
  @Autowired ListSuppliers listSuppliers;
  @Autowired GetSupplier getSupplier;
  @Autowired UpdateSupplier updateSupplier;
  @Autowired ChangeSupplierStatus changeSupplierStatus;
  @Autowired SupplierRepository suppliers;
  @Autowired PlatformTransactionManager transactions;
  @Autowired CreateClient createClient;
  @Autowired ClientRepository clients;
  @Autowired ListClients listClients;
  @Autowired JwtDecoder jwtDecoder;
  @LocalServerPort int port;
  @Autowired DataSource dataSource;
  @Autowired EntityManagerFactory entityManagerFactory;
  @Autowired Flyway flyway;
  @Autowired Environment environment;

  @Autowired ClientCategoryRepository clientCategories;

  @DynamicPropertySource
  static void configure(DynamicPropertyRegistry registry) {
    if (!COMPANY.isRunning()) {
      COMPANY.start();
    }
    registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> true);
    registry.add("sanigest.company-access.enabled", () -> true);
    registry.add("sanigest.company-access.origin", COMPANY::baseUrl);
    registry.add("CATALOG_INVENTORY_ORIGIN", COMPANY::baseUrl);
    registry.add("CATALOG_SALES_ORIGIN", COMPANY::baseUrl);
    registry.add("sanigest.company-access.issuer", () -> "catalog-test-internal");
    registry.add("sanigest.company-access.private-key-location", COMPANY_ACCESS_KEY::toUri);
    registry.add("JWT_ISSUER", () -> ISSUER);
    registry.add("JWT_AUDIENCE", () -> AUDIENCE);
    registry.add("CATALOG_DB_HOST", POSTGRES::getHost);
    registry.add("CATALOG_DB_PORT", () -> POSTGRES.getMappedPort(5432));
    registry.add("CATALOG_DB_PASSWORD", () -> PASSWORDS.get("CATALOG_DB_PASSWORD"));
    registry.add("JWT_PUBLIC_KEY_LOCATION", () -> PUBLIC_KEY.toUri().toString());
  }

  @AfterAll
  static void stopCompany() throws IOException {
    COMPANY.stop();
    Files.deleteIfExists(PUBLIC_KEY);
    Files.deleteIfExists(COMPANY_ACCESS_KEY);
  }

  @AfterEach
  void resetCompany() {
    COMPANY.resetAll();
  }

  @Test
  @Tag("smoke")
  void servesPublicHealthProbesAndOpenApi() throws Exception {
    try (var http = HttpClient.newHttpClient()) {
      var mapper = JsonMapper.shared();
      for (String path :
          new String[] {
            "/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"
          }) {
        var response = get(http, path, null);
        assertThat(response.statusCode()).isEqualTo(200);
        var health = mapper.readTree(response.body());
        assertThat(health.path("status").asString()).isEqualTo("UP");
        if (path.equals("/actuator/health")) {
          assertThat(health)
              .isEqualTo(
                  mapper.readTree("{\"status\":\"UP\",\"groups\":[\"liveness\",\"readiness\"]}"));
        } else {
          assertThat(health).isEqualTo(mapper.readTree("{\"status\":\"UP\"}"));
        }
      }
      var response = get(http, "/v3/api-docs", null);
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.headers().firstValue("Content-Type"))
          .hasValueSatisfying(value -> assertThat(value).startsWith("application/json"));
      var document = mapper.readTree(response.body());
      assertThat(document.path("openapi").asString()).isEqualTo("3.0.3");
      assertThat(document.at("/paths/~1api~1v1~1clients/get/operationId").asString())
          .isEqualTo("listClients");
      assertThat(document.at("/paths/~1api~1v1~1suppliers/post/operationId").asString())
          .isEqualTo("createSupplier");
      assertThat(document.at("/paths/~1api~1v1~1catalog-items/post/operationId").asString())
          .isEqualTo("createCatalogItem");
      assertThat(document.at("/paths/~1api~1v1~1catalog-items/get/operationId").asString())
          .isEqualTo("listCatalogItems");
      assertThat(document.at("/paths/~1api~1v1~1catalog-items~1{id}/get/operationId").asString())
          .isEqualTo("getCatalogItem");
      assertThat(document.at("/paths/~1api~1v1~1catalog-items~1{id}/patch/operationId").asString())
          .isEqualTo("updateCatalogItem");
      assertThat(
              document
                  .at("/paths/~1api~1v1~1catalog-items~1{id}~1status/patch/operationId")
                  .asString())
          .isEqualTo("changeCatalogItemStatus");
      assertThat(document.path("info").path("title").asString())
          .isEqualTo("SaniGest Catalog Service");
      assertThat(
              document
                  .path("paths")
                  .path("/actuator/health")
                  .path("get")
                  .path("responses")
                  .has("200"))
          .isTrue();
    }
  }

  @Test
  @Tag("regression")
  void validatesJwtAndDeniesBusinessAccessWithoutCompanyAuthorization() throws Exception {
    var now = Instant.now();
    try (var http = HttpClient.newHttpClient()) {
      String path = "/api/v1/products?companyId=" + UUID.randomUUID();
      assertThat(get(http, path, null).statusCode()).isEqualTo(401);
      assertThat(get(http, path, "invalid").statusCode()).isEqualTo(401);
      assertThat(get(http, path, token(KEY, ISSUER, AUDIENCE, now.plusSeconds(120))).statusCode())
          .isEqualTo(403);
      assertThat(
              get(http, path, token(rsaKey(), ISSUER, AUDIENCE, now.plusSeconds(120))).statusCode())
          .isEqualTo(401);
      assertThat(
              get(http, path, token(KEY, "other-issuer", AUDIENCE, now.plusSeconds(120)))
                  .statusCode())
          .isEqualTo(401);
      assertThat(
              get(http, path, token(KEY, ISSUER, "other-audience", now.plusSeconds(120)))
                  .statusCode())
          .isEqualTo(401);
      assertThat(get(http, path, token(KEY, ISSUER, AUDIENCE, now.minusSeconds(120))).statusCode())
          .isEqualTo(401);
    }
  }

  @Test
  @Tag("regression")
  void startsJpaInValidationModeAndAppliesOnlyCatalogMigrations() {
    var jdbc = new JdbcTemplate(dataSource);
    assertThat(entityManagerFactory.isOpen()).isTrue();
    assertThat(entityManagerFactory.getProperties())
        .containsEntry("hibernate.hbm2ddl.auto", "validate");
    assertThat(environment.matchesProfiles("local & test")).isTrue();
    assertThat(jdbc.queryForMap("SELECT current_database() AS db, session_user AS role"))
        .containsAllEntriesOf(Map.of("db", CATALOG, "role", CATALOG));
    assertThat(
            jdbc.queryForObject(
                "SELECT schema_owner FROM information_schema.schemata WHERE schema_name = 'catalog'",
                String.class))
        .isEqualTo(CATALOG);
    assertThat(
            jdbc.queryForList(
                "SELECT schemaname || '.' || tablename FROM pg_tables WHERE schemaname IN ('public', 'catalog')",
                String.class))
        .containsExactlyInAnyOrder(
            "public.flyway_schema_history",
            "catalog.clients",
            "catalog.suppliers",
            "catalog.catalog_items",
            "catalog.client_categories",
            "catalog.client_countries",
            "catalog.client_provinces",
            "catalog.client_municipalities",
            "catalog.client_receipt_types",
            "catalog.supplier_categories",
            "catalog.supplier_items",
            "catalog.catalog_item_categories",
            "catalog.units_of_measure",
            "catalog.tax_classifications",
            "catalog.tax_rate_versions",
            "catalog.catalog_item_history");
    assertThat(flyway.info().applied())
        .extracting(migration -> migration.getScript())
        .containsExactly(
            "V1__create_catalog_schema.sql",
            "V2__create_clients.sql",
            "V3__index_clients_by_company_and_name.sql",
            "V4__allow_inactive_clients.sql",
            "V5__create_suppliers.sql",
            "V6__support_supplier_management.sql",
            "V7__create_catalog_items.sql",
            "V8__support_catalog_item_management.sql",
            "V9__create_client_reference_catalogs.sql",
            "V10__extend_client_commercial_profile.sql",
            "V11__create_supplier_categories.sql",
            "V12__extend_supplier_commercial_profile.sql",
            "V13__create_supplier_item_relationships.sql",
            "V14__create_catalog_item_reference_data.sql",
            "V15__extend_catalog_item_commercial_profile.sql",
            "V16__create_catalog_item_history.sql",
            "V17__preserve_required_commercial_fields.sql",
            "V18__require_commercial_fields_on_insert.sql");
    flyway.validate();
    assertThat(flyway.migrate().migrationsExecuted).isZero();
  }

  @Test
  @Tag("regression")
  void postgresRejectsCrossServiceCredentialsAndConnections() {
    for (String neighbor : new String[] {"identity", "company"}) {
      String database = "sanigest_" + neighbor;
      String neighborPassword = PASSWORDS.get(neighbor.toUpperCase(Locale.ROOT) + "_DB_PASSWORD");
      assertConnectionRejected(database, CATALOG, PASSWORDS.get("CATALOG_DB_PASSWORD"), "42501");
      assertConnectionRejected(CATALOG, database, neighborPassword, "42501");
      assertConnectionRejected(CATALOG, CATALOG, neighborPassword, "28P01");
    }
  }

  @Test
  @Tag("regression")
  void connectionPoolRejectsPrivilegedCredentialsBeforeUse() {
    var config = new HikariConfig();
    config.setJdbcUrl(POSTGRES.getJdbcUrl().replaceFirst("/postgres$", "/" + CATALOG));
    config.setUsername(POSTGRES.getUsername());
    config.setPassword(POSTGRES.getPassword());
    config.setConnectionInitSql(
        environment.getRequiredProperty("spring.datasource.hikari.connection-init-sql"));

    assertThatThrownBy(
            () -> {
              try (var unexpected = new HikariDataSource(config)) {
                assertThat(unexpected.isClosed()).isFalse();
              }
            })
        .rootCause()
        .hasMessageContaining("Catalog requires the sanigest_catalog database and role");
  }

  @Test
  @Tag("smoke")
  void createsAndPersistsOnlyInTheAuthorizedCompanyThenRejectsADuplicate() throws Exception {
    var companyId = UUID.randomUUID();
    var otherCompanyId = UUID.randomUUID();
    var taxId = UUID.randomUUID().toString();
    var token = authorizedToken(companyId);
    try (var http = HttpClient.newHttpClient()) {
      var response =
          http.send(
              clientRequest(
                  token,
                  """
                  {"name":" Client ","taxId":" %s ","email":"sample@example.org","phone":"809-555-1234",
                   "address":"Santo Domingo","clientType":"LEGAL","countryCode":"US"}
                  """
                      .formatted(taxId),
                  otherCompanyId),
              HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).isEqualTo(201);
      var mapper = JsonMapper.shared();
      var body = mapper.readTree(response.body());
      var id = UUID.fromString(body.path("id").asString());
      assertThat(body.path("name").asString()).isEqualTo("Client");
      assertThat(body.path("taxId").asString()).isEqualTo(taxId);
      assertThat(body.path("status").asString()).isEqualTo("ACTIVE");
      assertThat(body.path("clientType").asString()).isEqualTo("LEGAL");
      assertThat(body.path("paymentTerms").asString()).isEqualTo("CASH");
      assertThat(body.path("categoryId").isString()).isTrue();
      var row =
          new JdbcTemplate(dataSource)
              .queryForMap(
                  "SELECT company_id, name, tax_id, status, email, phone, address FROM catalog.clients WHERE id = ?",
                  id);
      assertThat(row)
          .containsExactlyInAnyOrderEntriesOf(
              Map.of(
                  "company_id",
                  companyId,
                  "name",
                  "Client",
                  "tax_id",
                  taxId,
                  "status",
                  "ACTIVE",
                  "email",
                  "sample@example.org",
                  "phone",
                  "809-555-1234",
                  "address",
                  "Santo Domingo"));

      COMPANY.verify(
          1, getRequestedFor(urlEqualTo(PATH)).withHeader(USER_ASSERTION, equalTo(token)));

      var duplicate =
          http.send(
              clientRequest(token, withTaxId(taxId), otherCompanyId),
              HttpResponse.BodyHandlers.ofString());
      assertThat(duplicate.statusCode()).isEqualTo(409);
      assertThat(mapper.readTree(duplicate.body()).path("errorCode").asString())
          .isEqualTo("DUPLICATE_TAX_ID");
    }
  }

  @Test
  @Tag("regression")
  void enforcesUniquenessWithinOneCompanyEvenAcrossOverlappingPosts() throws Exception {
    var companyId = UUID.randomUUID();
    var token = authorizedToken(companyId);
    var taxId = UUID.randomUUID().toString();
    var request = clientRequest(token, withTaxId(taxId), null);
    try (var http = HttpClient.newHttpClient()) {
      var first = http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
      var second = http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
      assertThat(
              new int[] {
                first.get(10, TimeUnit.SECONDS).statusCode(),
                second.get(10, TimeUnit.SECONDS).statusCode()
              })
          .containsExactlyInAnyOrder(201, 409);
      assertThat(postStatus(http, authorizedToken(UUID.randomUUID()), withTaxId(taxId)))
          .isEqualTo(201);
      var nullTaxIdToken = authorizedToken(companyId);
      assertThat(postStatus(http, nullTaxIdToken, "{\"name\":\"First\",\"clientType\":\"LEGAL\"}"))
          .isEqualTo(201);
      assertThat(
              postStatus(
                  http,
                  nullTaxIdToken,
                  "{\"name\":\"Second\",\"clientType\":\"LEGAL\",\"taxId\":null}"))
          .isEqualTo(201);
      var jdbc = new JdbcTemplate(dataSource);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM catalog.clients WHERE company_id = ? AND tax_id = ?",
                  Long.class,
                  companyId,
                  taxId))
          .isEqualTo(1L);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM catalog.clients WHERE company_id = ? AND tax_id IS NULL",
                  Long.class,
                  companyId))
          .isEqualTo(2L);
    }
  }

  @Test
  @Tag("regression")
  void deniesPostsWithInvalidAuthenticationOrWithoutCurrentCompanyPermission() throws Exception {
    var now = Instant.now();
    var companyId = UUID.randomUUID();
    var advisor = session("ADVISOR");
    stubFailure(advisor, 403);
    var viewer = membershipToken(companyId, MembershipRole.VIEWER, false);
    var operator = membershipToken(companyId, MembershipRole.OPERATOR, false);
    var unavailable = session("REGULAR");
    stubFailure(unavailable, 503);
    var taxId = UUID.randomUUID().toString();
    var body =
        """
        {"name":"Denied","taxId":"%s","clientType":"LEGAL","countryCode":"US"}
        """
            .formatted(taxId, companyId, UUID.randomUUID());
    try (var http = HttpClient.newHttpClient()) {
      assertThat(postStatus(http, null, body)).isEqualTo(401);
      assertThat(postStatus(http, "invalid", body)).isEqualTo(401);
      assertThat(postStatus(http, token(KEY, ISSUER, AUDIENCE, now.minusSeconds(120)), body))
          .isEqualTo(401);
      assertThat(postStatus(http, token(rsaKey(), ISSUER, AUDIENCE, now.plusSeconds(120)), body))
          .isEqualTo(401);
      assertThat(
              http.send(
                      clientRequest(advisor, body, companyId), HttpResponse.BodyHandlers.ofString())
                  .statusCode())
          .isEqualTo(403);
      assertThat(postStatus(http, viewer, body)).isEqualTo(403);
      assertThat(postStatus(http, operator, body)).isEqualTo(403);
      assertThat(postStatus(http, unavailable, body)).isEqualTo(503);
    }
    assertThat(
            new JdbcTemplate(dataSource)
                .queryForObject(
                    "SELECT count(*) FROM catalog.clients WHERE tax_id = ?", Long.class, taxId))
        .isZero();
  }

  @Test
  @Tag("smoke")
  void allowsAnExplicitOperatorGrantAndDeniesAViewerWithoutPersisting() throws Exception {
    var companyId = UUID.randomUUID();
    var granted = membershipToken(companyId, MembershipRole.OPERATOR, true);
    var viewer = membershipToken(companyId, MembershipRole.VIEWER, false);
    var allowedTaxId = UUID.randomUUID().toString();
    var deniedTaxId = UUID.randomUUID().toString();
    try (var http = HttpClient.newHttpClient()) {
      assertThat(postStatus(http, granted, withTaxId(allowedTaxId))).isEqualTo(201);
      assertThat(postStatus(http, viewer, withTaxId(deniedTaxId))).isEqualTo(403);
      stubFailure(granted, 403);
      assertThat(postStatus(http, granted, withTaxId(deniedTaxId))).isEqualTo(403);
    }
    assertThat(
            new JdbcTemplate(dataSource)
                .queryForList(
                    "SELECT company_id FROM catalog.clients WHERE tax_id IN (?, ?)",
                    UUID.class,
                    allowedTaxId,
                    deniedTaxId))
        .containsExactly(companyId);
  }

  @Test
  @Tag("regression")
  void rejectsDirectApplicationCallsWithoutAuthentication() {
    assertThatThrownBy(
            () -> createClient.execute(new ClientDetails("Client", null, null, null, null)))
        .isInstanceOf(AccessDeniedException.class);
    COMPANY.verify(0, getRequestedFor(urlEqualTo(PATH)));
  }

  @Test
  @Tag("regression")
  void rejectsDirectApplicationCallsFromAViewerDespiteJwtClaims() throws Exception {
    var token = membershipToken(UUID.randomUUID(), MembershipRole.VIEWER, true);
    var taxId = UUID.randomUUID().toString();
    var jwt = jwtDecoder.decode(token);
    SecurityContextHolder.getContext()
        .setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    var request = new org.springframework.mock.web.MockHttpServletRequest();
    request.addHeader("X-Company-Id", SELECTORS.get(token).toString());
    org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
        new org.springframework.web.context.request.ServletRequestAttributes(request));
    try {
      var details = new ClientDetails("Client", taxId, null, null, null);
      assertThatThrownBy(() -> createClient.execute(details))
          .isInstanceOfSatisfying(
              ClientCreationException.class,
              failure ->
                  assertThat(failure.reason()).isEqualTo(ClientCreationException.Reason.FORBIDDEN));
    } finally {
      SecurityContextHolder.clearContext();
      org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
    }
    COMPANY.verify(1, getRequestedFor(urlEqualTo(PATH)).withHeader(USER_ASSERTION, equalTo(token)));
    assertThat(
            new JdbcTemplate(dataSource)
                .queryForObject(
                    "SELECT count(*) FROM catalog.clients WHERE tax_id = ?", Long.class, taxId))
        .isZero();
  }

  @Test
  @Tag("smoke")
  void createsAndListsOnlyAuthorizedClientsWithFiltersAndPagination() throws Exception {
    var companyA = UUID.randomUUID();
    var companyB = UUID.randomUUID();
    try (var http = HttpClient.newHttpClient()) {
      var writerA = authorizedToken(companyA);
      var writerB = authorizedToken(companyB);
      var one = createForListing(http, writerA, "Alpha Clinic", "A-100");
      var two = createForListing(http, writerA, "Alpha Clinic", "a-200");
      var last = createForListing(http, writerA, "Zulu Clinic", null);
      var other = createForListing(http, writerB, "Alpha Clinic", "A-100");
      var readerA = membershipToken(companyA, MembershipRole.VIEWER, false, Scope.READ);
      var readerB = membershipToken(companyB, MembershipRole.OWNER, false, Scope.READ);
      var ids = List.of(one, two).stream().sorted().toList();

      var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
      long before = statistics.getPrepareStatementCount();
      var first = listPage(http, readerA, "?size=2", companyB);
      assertThat(statistics.getPrepareStatementCount() - before).isEqualTo(2);
      assertPage(first, 0, 2, 3, 2, ids.toArray(String[]::new));
      assertThat(first.path("content").get(0).propertyNames())
          .contains(
              "id",
              "name",
              "taxId",
              "status",
              "phone",
              "clientType",
              "paymentTerms",
              "categoryId",
              "categoryName");
      assertPage(listPage(http, readerA, "?size=2&page=1", null), 1, 2, 3, 2, last);
      assertPage(
          listPage(http, readerA, "?size=2&page=2147483647", null), Integer.MAX_VALUE, 2, 3, 2);
      assertPage(
          listPage(http, readerA, "?search=%20aLpHa%20&status=%20active%20", null),
          0,
          20,
          2,
          1,
          ids.toArray(String[]::new));
      assertPage(listPage(http, readerA, "?search=A-2", null), 0, 20, 1, 1, two);
      assertPage(listPage(http, readerA, "?status=INACTIVE&search=alpha", null), 0, 20, 0, 0);
      assertPage(listPage(http, readerA, "?status=INACTIVE", null), 0, 20, 0, 0);
      assertPage(listPage(http, readerA, "?search=missing", null), 0, 20, 0, 0);
      assertPage(listPage(http, readerA, "?search=%25_", null), 0, 20, 0, 0);
      assertPage(
          listPage(http, readerA, "?search=%20&status=&page=&size=", null),
          0,
          20,
          3,
          1,
          ids.get(0),
          ids.get(1),
          last);
      assertPage(
          listPage(http, readerB, "?search=a-100&companyId=" + companyA, companyA),
          0,
          20,
          1,
          1,
          other);
      assertThat(listPage(http, readerA, "?search=zulu", null).at("/content/0/taxId").isNull())
          .isTrue();
      assertThat(listPage(http, readerA, "?search=a-100", null).at("/content/0/taxId").asString())
          .isEqualTo("A-100");
    }
  }

  @Test
  @Tag("regression")
  void requiresCurrentReadPermissionAndFailsClosed() throws Exception {
    var companyA = UUID.randomUUID();
    var companyB = UUID.randomUUID();
    var path = "/api/v1/clients";
    try (var http = HttpClient.newHttpClient()) {
      assertThat(get(http, path, null).statusCode()).isEqualTo(401);
      assertThat(get(http, path, "invalid").statusCode()).isEqualTo(401);
      var expired = token(KEY, ISSUER, AUDIENCE, Instant.now().minusSeconds(120));
      assertThat(get(http, path, expired).statusCode()).isEqualTo(401);

      for (var role : MembershipRole.values()) {
        var reader = membershipToken(companyA, role, false, Scope.READ);
        assertPage(listPage(http, reader, "", null), 0, 20, 0, 0);
        stubFailure(reader, 403);
        assertThat(get(http, path, reader).statusCode()).isEqualTo(403);
      }

      var writeOnly = session("REGULAR");
      stubFailure(writeOnly, 403);
      var denied = get(http, path + "?search=%27%20OR%201%3D1", writeOnly);
      assertThat(denied.statusCode()).isEqualTo(403);
      assertThat(JsonMapper.shared().readTree(denied.body()).path("errorCode").asString())
          .isEqualTo("FORBIDDEN");

      var advisor = session("ADVISOR");
      stubFailure(advisor, 403);
      assertThat(get(http, path, advisor).statusCode()).isEqualTo(403);
      // A grant to A is only reachable through a delegated request, without a membership assertion.
      var advisorSubject = SignedJWT.parse(advisor).getJWTClaimsSet().getSubject();
      COMPANY.stubFor(
          WireMock.get(urlEqualTo(PATH))
              .withHeader(USER_ASSERTION, WireMock.absent())
              .willReturn(
                  aResponse()
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          """
                          {"userId":"%s","companyId":"%s","module":"CATALOG","scope":"READ","mode":"DELEGATED"}
                          """
                              .formatted(advisorSubject, companyA))));
      assertThat(get(http, path + "?companyId=" + companyB, advisor, companyB).statusCode())
          .isEqualTo(403);
      COMPANY.verify(
          0, getRequestedFor(urlEqualTo(PATH)).withHeader(USER_ASSERTION, WireMock.absent()));

      var unavailable = session("REGULAR");
      stubFailure(unavailable, 503);
      var response = get(http, path, unavailable);
      assertThat(response.statusCode()).isEqualTo(503);
      assertThat(JsonMapper.shared().readTree(response.body()).path("errorCode").asString())
          .isEqualTo("AUTHORIZATION_UNAVAILABLE");

      var slow = session("REGULAR");
      COMPANY.stubFor(
          WireMock.get(urlEqualTo(PATH))
              .withHeader(USER_ASSERTION, equalTo(slow))
              .willReturn(aResponse().withStatus(403).withFixedDelay(3000)));
      assertThat(get(http, path, slow).statusCode()).isEqualTo(503);
    }
  }

  @Test
  @Tag("regression")
  void rejectsDirectListingWithoutAuthenticatedContext() {
    assertThatThrownBy(() ->
            listClients.execute(
                new ClientSearchCriteria(null, null, null, null, null, false), 0, 20))
        .isInstanceOf(AccessDeniedException.class);
    COMPANY.verify(0, getRequestedFor(urlEqualTo(PATH)));
  }

  @Test
  @Tag("smoke")
  void editsDeactivatesAndReactivatesAClientWithoutChangingItsIdentity() throws Exception {
    var companyId = UUID.randomUUID();
    var writer = authorizedToken(companyId);
    var reader = membershipToken(companyId, MembershipRole.VIEWER, false, Scope.READ);
    try (var http = HttpClient.newHttpClient()) {
      var id = createForListing(http, writer, "Smoke client", UUID.randomUUID().toString());
      var path = "/api/v1/clients/" + id;
      var original = successfulBody(get(http, path, reader));
      var edited =
          successfulBody(
              patch(
                  http,
                  path,
                  writer,
                  """
                  {"email":"updated@example.org"}
                  """,
                  null));
      assertThat(edited.path("email").asString()).isEqualTo("updated@example.org");
      assertThat(edited.path("name")).isEqualTo(original.path("name"));
      assertThat(edited.path("taxId")).isEqualTo(original.path("taxId"));

      var inactive =
          successfulBody(patch(http, path + "/status", writer, "{\"status\":\"INACTIVE\"}", null));
      assertThat(inactive.path("id").asString()).isEqualTo(id);
      assertThat(inactive.path("status").asString()).isEqualTo("INACTIVE");
      assertThat(successfulBody(get(http, path, reader))).isEqualTo(inactive);
      assertThat(listPage(http, reader, "?status=INACTIVE", null).path("content"))
          .extracting(item -> item.path("id").asString())
          .containsExactly(id);

      var jdbc = new JdbcTemplate(dataSource);
      var revision =
          jdbc.queryForObject(
              "SELECT revision FROM catalog.clients WHERE id = ?", Long.class, UUID.fromString(id));
      assertThat(
              successfulBody(
                  patch(http, path + "/status", writer, "{\"status\":\"INACTIVE\"}", null)))
          .isEqualTo(inactive);
      assertThat(
              jdbc.queryForObject(
                  "SELECT revision FROM catalog.clients WHERE id = ?",
                  Long.class,
                  UUID.fromString(id)))
          .isEqualTo(revision);

      var reactivated =
          successfulBody(patch(http, path + "/status", writer, "{\"status\":\"ACTIVE\"}", null));
      assertThat(reactivated).isEqualTo(edited);
      assertThat(successfulBody(get(http, path, reader))).isEqualTo(reactivated);
      assertPage(listPage(http, reader, "?status=ACTIVE", null), 0, 20, 1, 1, id);
    }
  }

  @Test
  @Tag("regression")
  void isolatesDetailAndBothPatchesAndHonorsCurrentWriteAuthorization() throws Exception {
    var companyId = UUID.randomUUID();
    var owner = authorizedToken(companyId);
    var admin = membershipToken(companyId, MembershipRole.ADMIN, false);
    var viewer = membershipToken(companyId, MembershipRole.VIEWER, false);
    var operator = membershipToken(companyId, MembershipRole.OPERATOR, false);
    var grantedOperator = membershipToken(companyId, MembershipRole.OPERATOR, true);
    var advisor = session("ADVISOR");
    stubFailure(advisor, 403);
    var otherCompany = UUID.randomUUID();
    var otherWriter = authorizedToken(otherCompany);
    var otherReader = membershipToken(otherCompany, MembershipRole.OWNER, false, Scope.READ);
    var reader = membershipToken(companyId, MembershipRole.OWNER, false, Scope.READ);
    try (var http = HttpClient.newHttpClient()) {
      var id = createForListing(http, owner, "Protected", UUID.randomUUID().toString());
      var path = "/api/v1/clients/" + id;
      var baseline = successfulBody(patch(http, path, admin, "{\"address\":\"SD\"}", otherCompany));
      var payload = "{\"email\":\"denied@example.org\"}";
      var statusPayload = "{\"status\":\"INACTIVE\"}";
      assertThat(patch(http, path, null, payload, null).statusCode()).isEqualTo(401);
      assertThat(patch(http, path, "invalid", payload, null).statusCode()).isEqualTo(401);
      for (var token : List.of(viewer, operator, advisor)) {
        assertThat(patch(http, path, token, payload, companyId).statusCode()).isEqualTo(403);
        assertThat(patch(http, path + "/status", token, statusPayload, companyId).statusCode())
            .isEqualTo(403);
      }
      var foreign = get(http, path, otherReader, companyId);
      var absent = get(http, "/api/v1/clients/" + UUID.randomUUID(), otherReader);
      assertThat(foreign.statusCode()).isEqualTo(404);
      assertThat(absent.statusCode()).isEqualTo(404);
      var mapper = JsonMapper.shared();
      assertThat(mapper.readTree(foreign.body()).path("detail"))
          .isEqualTo(mapper.readTree(absent.body()).path("detail"));
      assertThat(patch(http, path, otherWriter, payload, companyId).statusCode()).isEqualTo(404);
      assertThat(patch(http, path + "/status", otherWriter, statusPayload, companyId).statusCode())
          .isEqualTo(404);
      assertThat(successfulBody(get(http, path, reader))).isEqualTo(baseline);

      var allowed = successfulBody(patch(http, path, grantedOperator, "{\"address\":null}", null));
      assertThat(allowed.path("address").isNull()).isTrue();
      stubFailure(grantedOperator, 403);
      assertThat(patch(http, path, grantedOperator, payload, null).statusCode()).isEqualTo(403);
      stubFailure(owner, 503);
      assertThat(patch(http, path + "/status", owner, statusPayload, null).statusCode())
          .isEqualTo(503);
      assertThat(successfulBody(get(http, path, reader))).isEqualTo(allowed);
    }
  }

  @Test
  @Tag("regression")
  void rejectsDuplicateTaxIdAndKeepsInactiveTaxIdsReserved() throws Exception {
    var companyId = UUID.randomUUID();
    var writer = authorizedToken(companyId);
    var taxId = UUID.randomUUID().toString();
    try (var http = HttpClient.newHttpClient()) {
      var first = "/api/v1/clients/" + createForListing(http, writer, "First", taxId);
      var second = "/api/v1/clients/" + createForListing(http, writer, "Second", null);
      successfulBody(patch(http, first + "/status", writer, "{\"status\":\"INACTIVE\"}", null));
      var response = patch(http, second, writer, "{\"taxId\":\" %s \"}".formatted(taxId), null);
      assertThat(response.statusCode()).isEqualTo(409);
      assertThat(JsonMapper.shared().readTree(response.body()).path("errorCode").asString())
          .isEqualTo("DUPLICATE_TAX_ID");
      assertThat(
              successfulBody(
                      patch(
                          http,
                          second,
                          writer,
                          "{\"taxId\":\" %s \"}".formatted(taxId + "-2"),
                          null))
                  .path("taxId")
                  .asString())
          .isEqualTo(taxId + "-2");
      successfulBody(patch(http, first + "/status", writer, "{\"status\":\"ACTIVE\"}", null));
    }
  }

  @Test
  @Tag("regression")
  void categoryInitializationIsTransactionalAndIdempotentAcrossConnections() throws Exception {
    var company = UUID.randomUUID();
    var transaction = new TransactionTemplate(transactions);
    transaction.executeWithoutResult(status -> {
      clientCategories.initialize(company);
      assertThat(clientCategories.list(company)).hasSize(4);
      status.setRollbackOnly();
    });
    assertThat(clientCategories.list(company)).isEmpty();

    var inserted = new CountDownLatch(1);
    var secondConnection = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first =
          pool.submit(() ->
              transaction.executeWithoutResult(status -> {
                clientCategories.initialize(company);
                inserted.countDown();
                await(secondConnection);
              }));
      var second =
          pool.submit(() ->
              transaction.executeWithoutResult(status -> {
                await(inserted);
                secondConnection.countDown();
                clientCategories.initialize(company);
              }));
      first.get(10, TimeUnit.SECONDS);
      second.get(10, TimeUnit.SECONDS);
    }
    var categories = clientCategories.list(company);
    assertThat(categories)
        .extracting(category -> category.code())
        .containsExactlyInAnyOrder("REGULAR", "FREQUENT", "WHOLESALE", "CORPORATE");
    transaction.executeWithoutResult(status -> clientCategories.initialize(company));
    assertThat(clientCategories.list(company)).containsExactlyElementsOf(categories);
  }

  @Test
  @Tag("regression")
  void rejectsAStaleConcurrentUpdateWithoutLosingTheCommittedChange() throws Exception {
    var original =
        Client.register(
            UUID.randomUUID(), new ClientDetails("Concurrent client", null, null, null, null));
    seedClient(original);
    var snapshotRead = new CountDownLatch(1);
    var committed = new CountDownLatch(1);
    try (var executor = Executors.newSingleThreadExecutor()) {
      var stale =
          executor.submit(() ->
              clients.update(
                  original.companyId(),
                  original.id(),
                  current -> {
                    snapshotRead.countDown();
                    await(committed);
                    return current.changeStatus(ClientStatus.INACTIVE);
                  }));
      try {
        await(snapshotRead);
        clients.update(
            original.companyId(),
            original.id(),
            current ->
                current.updateDetails(
                    ClientPatchRequest.details(
                        JsonMapper.shared().valueToTree(Map.of("email", "saved@example.org")))));
      } finally {
        committed.countDown();
      }
      assertThatThrownBy(() -> stale.get(10, TimeUnit.SECONDS))
          .hasCauseInstanceOf(ClientAccessException.class)
          .satisfies(failure ->
              assertThat(((ClientAccessException) failure.getCause()).reason())
                  .isEqualTo(ClientAccessException.Reason.CLIENT_UPDATE_CONFLICT));
    }
    var stored = clients.find(original.companyId(), original.id()).orElseThrow();
    assertThat(stored.status()).isEqualTo(ClientStatus.ACTIVE);
    assertThat(stored.details().email()).isEqualTo("saved@example.org");
  }

  @Test
  @Tag("regression")
  void migratesExistingCatalogRecordsThroughV8() throws Exception {
    try (var connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var statement = connection.createStatement()) {
      statement.execute("CREATE DATABASE catalog_upgrade OWNER sanigest_catalog");
    }

    var source =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl().replace("/postgres", "/catalog_upgrade"),
            CATALOG,
            PASSWORDS.get("CATALOG_DB_PASSWORD"));
    var migration = Flyway.configure().dataSource(source).target("3").load();
    assertThat(migration.migrate().migrationsExecuted).isEqualTo(3);
    var jdbc = new JdbcTemplate(source);
    var id = UUID.randomUUID();
    var companyId = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO catalog.clients (id, company_id, name, tax_id, status)
        VALUES (?, ?, 'Existing', '123', 'ACTIVE')
        """,
        id,
        companyId);

    assertThat(
            Flyway.configure().dataSource(source).target("5").load().migrate().migrationsExecuted)
        .isEqualTo(2);
    var supplierId = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO catalog.suppliers (id, company_id, name, tax_id, contact)
        VALUES (?, ?, 'Existing supplier', '123', 'Laura')
        """,
        supplierId,
        companyId);

    assertThat(
            Flyway.configure().dataSource(source).target("7").load().migrate().migrationsExecuted)
        .isEqualTo(2);
    var itemId = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO catalog.catalog_items (id, company_id, name, type, price)
        VALUES (?, ?, 'Existing item', 'PRODUCT', 3200.50)
        """,
        itemId,
        companyId);
    assertThat(
            Flyway.configure().dataSource(source).target("8").load().migrate().migrationsExecuted)
        .isEqualTo(1);
    verifyClientProfileMigration(source, jdbc, companyId);
    assertThat(
            Flyway.configure().dataSource(source).target("13").load().migrate().migrationsExecuted)
        .isEqualTo(3);
    verifyCommercialItemMigration(source, jdbc, companyId, supplierId, itemId);

    assertThat(
            jdbc.queryForMap(
                """
                SELECT company_id, description, price, revision FROM catalog.catalog_items WHERE id = ?
                """,
                itemId))
        .containsEntry("company_id", companyId)
        .containsEntry("description", null)
        .containsEntry("price", new BigDecimal("3200.5000"))
        .containsEntry("revision", 8L);
    assertThat(
            jdbc.queryForMap(
                "SELECT company_id, tax_id, status, revision FROM catalog.suppliers WHERE id = ?",
                supplierId))
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("company_id", companyId, "tax_id", "123", "status", "ACTIVE", "revision", 6L));
    assertThat(
            jdbc.queryForMap(
                """
                SELECT company_id, name, tax_id, status, revision FROM catalog.clients WHERE id = ?
                """,
                id))
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "company_id",
                companyId,
                "name",
                "Existing",
                "tax_id",
                "123",
                "status",
                "ACTIVE",
                "revision",
                0L));
    jdbc.update("UPDATE catalog.clients SET status = 'INACTIVE' WHERE id = ?", id);
    assertThatThrownBy(
            () -> jdbc.update("UPDATE catalog.clients SET status = 'DELETED' WHERE id = ?", id))
        .isInstanceOf(DataIntegrityViolationException.class);
    verifyRequiredCommercialFieldsCannotBeCleared(jdbc, supplierId, itemId);
  }

  private static void verifyRequiredCommercialFieldsCannotBeCleared(
      JdbcTemplate jdbc, UUID supplierId, UUID itemId) {
    assertThat(
            jdbc.queryForMap(
                "SELECT supply_type, payment_terms, category_id FROM catalog.suppliers WHERE id = ?",
                supplierId))
        .containsEntry("supply_type", null)
        .containsEntry("payment_terms", null)
        .containsEntry("category_id", null);
    jdbc.update("UPDATE catalog.catalog_items SET unit_code = 'UNIT' WHERE id = ?", itemId);
    jdbc.update("UPDATE catalog.suppliers SET payment_terms = 'CASH' WHERE id = ?", supplierId);
    assertThatThrownBy(() ->
            jdbc.update("UPDATE catalog.catalog_items SET unit_code = NULL WHERE id = ?", itemId))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() ->
            jdbc.update(
                "UPDATE catalog.suppliers SET payment_terms = NULL WHERE id = ?", supplierId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private static void verifyCommercialItemMigration(
      DataSource source, JdbcTemplate jdbc, UUID companyId, UUID supplierId, UUID itemId) {
    jdbc.update("UPDATE catalog.suppliers SET revision = 6 WHERE id = ?", supplierId);
    jdbc.update("UPDATE catalog.catalog_items SET revision = 8 WHERE id = ?", itemId);
    jdbc.update(
        """
        INSERT INTO catalog.supplier_items (company_id, supplier_id, catalog_item_id, is_primary)
        VALUES (?, ?, ?, TRUE)
        """,
        companyId,
        supplierId,
        itemId);
    assertThat(
            Flyway.configure().dataSource(source).target("17").load().migrate().migrationsExecuted)
        .isEqualTo(4);
    assertThat(Flyway.configure().dataSource(source).load().migrate().migrationsExecuted)
        .isEqualTo(1);
    assertIncompleteInsertsRejected(jdbc, companyId);
    assertThat(
            jdbc.queryForObject(
                "SELECT is_primary FROM catalog.supplier_items WHERE catalog_item_id = ?",
                Boolean.class,
                itemId))
        .isTrue();
    assertThat(
            jdbc.queryForMap(
                """
                SELECT sku, estimated_cost, tax_classification_code, available_for_purchase
                FROM catalog.catalog_items WHERE id = ?
                """,
                itemId))
        .allSatisfy((key, value) -> assertThat(value).isNull());
  }

  private static void verifyClientProfileMigration(
      DriverManagerDataSource source, JdbcTemplate jdbc, UUID companyId) {
    var historical = UUID.randomUUID();
    var historicalDuplicate = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO catalog.clients (id, company_id, name, tax_id, status, revision)
        VALUES (?, ?, 'Con separadores', '001-0000001-2', 'INACTIVE', 7),
               (?, ?, 'Sin separadores', '00100000012', 'ACTIVE', 3)
        """,
        historical,
        companyId,
        historicalDuplicate,
        companyId);
    var latest = Flyway.configure().dataSource(source).target("10").load();
    assertThat(latest.migrate().migrationsExecuted).isEqualTo(2);
    assertThat(
            jdbc.queryForMap(
                """
                SELECT tax_id, tax_id_key, revision, status, client_type, payment_terms, category_id,
                       country_code, credit_limit FROM catalog.clients WHERE id = ?
                """,
                historical))
        .containsEntry("tax_id", "001-0000001-2")
        .containsEntry("tax_id_key", "00100000012")
        .containsEntry("revision", 7L)
        .containsEntry("status", "INACTIVE")
        .containsEntry("client_type", null)
        .containsEntry("payment_terms", null)
        .containsEntry("category_id", null)
        .containsEntry("country_code", null)
        .containsEntry("credit_limit", null);
    assertThat(
            jdbc.queryForObject(
                """
                SELECT count(*) FROM catalog.clients WHERE company_id = ? AND tax_id_key = '00100000012'
                """,
                Long.class,
                companyId))
        .isEqualTo(2L);
    latest.validate();
  }

  @Test
  @Tag("regression")
  void readsThePersistedEditAndStatusFromAFreshApplicationContext() {
    var original =
        Client.register(
            UUID.randomUUID(), new ClientDetails("Persistent client", "321", null, null, null));
    seedClient(original);
    var stored =
        clients.update(
            original.companyId(),
            original.id(),
            current ->
                current
                    .updateDetails(
                        ClientPatchRequest.details(
                            JsonMapper.shared()
                                .valueToTree(Map.of("email", "persisted@example.org"))))
                    .changeStatus(ClientStatus.INACTIVE));

    try (var context =
        new SpringApplicationBuilder(CatalogServiceApplication.class)
            .profiles("local", "test")
            .run(
                "--server.port=0",
                "--CATALOG_DB_HOST=" + POSTGRES.getHost(),
                "--CATALOG_DB_PORT=" + POSTGRES.getMappedPort(5432),
                "--CATALOG_DB_PASSWORD=" + PASSWORDS.get("CATALOG_DB_PASSWORD"),
                "--JWT_ISSUER=" + ISSUER,
                "--JWT_AUDIENCE=" + AUDIENCE,
                "--JWT_PUBLIC_KEY_LOCATION=" + PUBLIC_KEY.toUri())) {
      assertThat(context.getBean(ClientRepository.class).find(original.companyId(), original.id()))
          .contains(stored);
    }
  }

  @Test
  @Tag("smoke")
  void completesTheCommercialProfileWorkflowOverHttp() throws Exception {
    var tenant = UUID.randomUUID();
    var otherTenant = UUID.randomUUID();
    var owner = scopedCatalogOwner(tenant);
    var foreign = scopedCatalogOwner(otherTenant);
    String path = "/api/v1/clients";
    try (var http = HttpClient.newHttpClient()) {
      String regular = verifyClientReferences(http, owner, path);
      var natural =
          createProfile(
              http,
              owner,
              """
              {"name":"Ana Pérez","clientType":"NATURAL","taxId":"001-0000001-2"}
              """);
      assertThat(natural.path("categoryId").asString()).isEqualTo(regular);
      assertThat(natural.path("countryCode").asString()).isEqualTo("DO");
      assertThat(natural.path("paymentTerms").asString()).isEqualTo("CASH");
      assertThat(natural.path("taxId").asString()).isEqualTo("00100000012");
      assertThat(natural.path("creditLimit").isNull()).isTrue();

      var legal =
          createProfile(
              http,
              owner,
              """
              {"name":"Central SRL","clientType":"LEGAL","taxId":"012-345678",
               "email":"VENTAS@example.org","phone":"8095550100","alternatePhone":"8295550100",
               "address":"Avenida principal","countryCode":"DO","provinceCode":"010000",
               "municipalityCode":"010100","preferredReceiptType":"FISCAL_CREDIT",
               "paymentTerms":"BOTH","categoryId":"%s","creditLimit":"50000.25","observations":"Central"}
              """
                  .formatted(regular));
      var id = legal.path("id").asString();
      var detailPath = path + "/" + id;
      var detailResponse = get(http, detailPath + "?expectedCompanyId=" + tenant, owner);
      assertThat(detailResponse.headers().firstValue("X-Catalog-Company-Id"))
          .contains(tenant.toString());
      assertThat(successfulBody(detailResponse)).isEqualTo(legal);
      assertThat(
              get(http, detailPath + "?expectedCompanyId=" + UUID.randomUUID(), owner).statusCode())
          .isEqualTo(403);

      var result =
          listPage(
              http,
              owner,
              "?search=ventas&status=ACTIVE&clientType=LEGAL&paymentTerms=CREDIT&categoryId="
                  + regular,
              tenant);
      assertPage(result, 0, 20, 1, 1, id);
      assertThat(result.path("content").get(0).path("categoryName").asString())
          .isEqualTo("Regular");
      for (var search : List.of("012345678", "8295550100", "8095550100", "CENTRAL")) {
        assertPage(listPage(http, owner, "?search=" + search, tenant), 0, 20, 1, 1, id);
      }

      var edited =
          successfulBody(
              patch(
                  http,
                  detailPath,
                  owner,
                  """
                  {"phone":"8495550123","alternatePhone":null,"paymentTerms":"CREDIT","creditLimit":"0.00"}
                  """,
                  null));
      assertThat(edited.path("phone").asString()).isEqualTo("8495550123");
      assertThat(edited.path("alternatePhone").isNull()).isTrue();
      assertThat(edited.path("creditLimit").asString()).isEqualTo("0.00");
      assertThat(edited.path("paymentTerms").asString()).isEqualTo("CREDIT");
      assertThat(edited.path("email")).isEqualTo(legal.path("email"));
      assertThat(successfulBody(get(http, detailPath, owner))).isEqualTo(edited);

      successfulBody(patch(http, detailPath + "/status", owner, "{\"status\":\"INACTIVE\"}", null));
      assertPage(listPage(http, owner, "?status=ACTIVE&search=central", tenant), 0, 20, 0, 0);
      assertThat(successfulBody(get(http, detailPath, owner)).path("categoryId").asString())
          .isEqualTo(regular);
      successfulBody(patch(http, detailPath + "/status", owner, "{\"status\":\"ACTIVE\"}", null));
      assertPage(listPage(http, owner, "?status=ACTIVE&search=central", tenant), 0, 20, 1, 1, id);
      assertThat(get(http, detailPath, foreign).statusCode()).isEqualTo(404);
      assertPage(listPage(http, foreign, "", null), 0, 20, 0, 0);
      verifyClientProfileRejections(http, owner, foreign, regular, detailPath);
    }
  }

  @Test
  @Tag("regression")
  void filtersBeforePagingAndRechecksEachCompanyForTheSameActor() throws Exception {
    var companyA = UUID.randomUUID();
    var companyB = UUID.randomUUID();
    var actor = session("REGULAR");
    stubScopedCatalogMembership(actor, companyA, MembershipRole.OWNER, Scope.READ);
    stubScopedCatalogMembership(actor, companyA, MembershipRole.OWNER, Scope.WRITE);
    stubScopedCatalogMembership(actor, companyB, MembershipRole.VIEWER, Scope.READ);
    stubScopedCatalogMembership(actor, companyB, MembershipRole.VIEWER, Scope.WRITE);
    try (var http = HttpClient.newHttpClient()) {
      var first = sendProfile(http, actor, companyA, "A%_", "BOTH");
      var last = sendProfile(http, actor, companyA, "B%_", "CREDIT");
      sendProfile(http, actor, companyA, "C%_", "CASH");
      sendProfile(http, actor, companyA, "D", "CREDIT");
      var category = first.path("categoryId").asString();
      String query =
          "?search=%25%5F&status=ACTIVE&clientType=LEGAL&paymentTerms=CREDIT&size=1&categoryId="
              + category
              + "&sort=name,desc&page=";
      String firstId = first.path("id").asString();
      String lastId = last.path("id").asString();
      assertPage(listPage(http, actor, query + 0, companyA), 0, 1, 2, 2, lastId);
      assertPage(listPage(http, actor, query + 1, companyA), 1, 1, 2, 2, firstId);
      assertPage(listPage(http, actor, query + 2, companyA), 2, 1, 2, 2);
      assertPage(listPage(http, actor, query + 0, companyB), 0, 1, 0, 0);

      var detailPath = "/api/v1/clients/" + firstId;
      assertThat(get(http, detailPath, actor, companyB).statusCode()).isEqualTo(404);
      assertThat(patch(http, detailPath, actor, "{\"name\":\"Denied\"}", companyB).statusCode())
          .isEqualTo(403);
      assertThat(
              patch(http, detailPath + "/status", actor, "{\"status\":\"INACTIVE\"}", companyB)
                  .statusCode())
          .isEqualTo(403);
      var categoriesA = successfulBody(get(http, "/api/v1/clients/categories", actor, companyA));
      var categoriesB = successfulBody(get(http, "/api/v1/clients/categories", actor, companyB));
      assertThat(categoriesA.valueStream().map(cat -> cat.path("id").asString()))
          .doesNotContainAnyElementsOf(
              categoriesB.valueStream().map(cat -> cat.path("id").asString()).toList());
      stubFailure(actor, 403);
      assertThat(get(http, detailPath, actor, companyA).statusCode()).isEqualTo(403);
      stubFailure(actor, 503);
      assertThat(get(http, detailPath, actor, companyA).statusCode()).isEqualTo(503);
    }
  }

  @Test
  @Tag("regression")
  void returnsActiveTenantSelectorsWithSearchAndCommercialEligibility() throws Exception {
    var company = UUID.randomUUID();
    var other = UUID.randomUUID();
    var owner = scopedCatalogOwner(company);
    var foreign = scopedCatalogOwner(other);
    var item = UUID.randomUUID();
    var supplier = UUID.randomUUID();
    items.insert(
        new CatalogItem(
            item,
            company,
            "Selector item",
            CatalogItemType.PRODUCT,
            BigDecimal.ONE,
            true,
            null,
            new CatalogItemProfile(null, null, null, "UNIT", null, "EXEMPT", true, false)));
    suppliers.insert(
        new Supplier(
            supplier,
            company,
            new SupplierDetails(
                "Selector supplier",
                null,
                "Contacto",
                null,
                null,
                new SupplierProfile(
                    null, SupplierSupplyType.BOTH, SupplierPaymentTerms.CASH, null, null, null)),
            SupplierStatus.ACTIVE));
    try (var http = HttpClient.newHttpClient()) {
      var client =
          sendProfile(http, owner, company, "Selector client", "BOTH").path("id").asString();
      var inactive =
          sendProfile(http, owner, company, "Selector inactive", "CASH").path("id").asString();
      assertThat(
              patch(
                      http,
                      "/api/v1/clients/" + inactive + "/status",
                      owner,
                      "{\"status\":\"INACTIVE\"}",
                      company)
                  .statusCode())
          .isEqualTo(200);
      var lookups =
          Map.of(
              "/api/v1/clients/options?search=selector",
                  Map.of("id", client, "name", "Selector client"),
              "/api/v1/suppliers/options?search=selector&supplyType=SERVICE",
                  Map.of("id", supplier.toString(), "name", "Selector supplier"),
              "/api/v1/catalog-items/options?search=selector&operation=SALE",
                  Map.of("id", item.toString(), "name", "Selector item"));
      for (var lookup : lookups.entrySet()) {
        var selected = successfulBody(get(http, lookup.getKey(), owner, company));
        assertThat(selected).isEqualTo(JsonMapper.shared().valueToTree(List.of(lookup.getValue())));
        assertThat(successfulBody(get(http, lookup.getKey(), foreign, other)).size()).isZero();
      }
      assertThat(
              successfulBody(get(http, "/api/v1/catalog-items/options?operation=PURCHASE", owner))
                  .size())
          .isZero();
      var options = successfulBody(get(http, "/api/v1/catalog/options", owner));
      assertThat(options.path("supplierCreditDays").get(0))
          .isEqualTo(JsonMapper.shared().readTree("{\"value\":null,\"label\":\"No aplica\"}"));
      assertThat(get(http, "/api/v1/catalog/options", null).statusCode()).isEqualTo(401);
    }
  }

  private JsonNode sendProfile(
      HttpClient http, String actor, UUID company, String name, String terms) throws Exception {
    var response =
        http.send(
            clientRequest(
                actor,
                """
                {"name":"%s","clientType":"LEGAL","paymentTerms":"%s"}
                """
                    .formatted(name, terms),
                company),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    return JsonMapper.shared().readTree(response.body());
  }

  @Test
  void composesClientFinancialsWithTheAuthorizedCompanyAndPreservesInactiveHistory()
      throws Exception {
    var company = UUID.randomUUID();
    var owner = scopedCatalogOwner(company);
    var foreign = scopedCatalogOwner(UUID.randomUUID());
    var base = "/internal/client-financials";
    try (var http = HttpClient.newHttpClient()) {
      var id = sendProfile(http, owner, company, "Cliente crédito", "CREDIT").path("id").asString();
      var second =
          sendProfile(http, owner, company, "Cliente efectivo", "CASH").path("id").asString();
      var detail = "/api/v1/clients/" + id;
      assertThat(
              patch(http, detail + "/status", owner, "{\"status\":\"INACTIVE\"}", company)
                  .statusCode())
          .isEqualTo(200);
      var batch =
          WireMock.post(WireMock.urlPathEqualTo(base + "/balances"))
              .withRequestBody(
                  WireMock.equalToJson("[\"" + id + "\",\"" + second + "\"]", true, false));
      stubSales(batch, owner, company, "{\"" + id + "\":35.00}");
      stubSales(
          WireMock.get(WireMock.urlPathEqualTo(base + "/summary")),
          owner,
          company,
          """
          {"clientsWithPendingBalance":1,"totalPending":35.00,"asOf":"2026-11-26T12:00:00Z"}
          """);
      stubSales(
          WireMock.get(WireMock.urlPathEqualTo(base + "/" + id + "/summary")),
          owner,
          company,
          """
          {"balance":35.00,"creditExposure":35.00,"totalInvoiced":50.00,
           "paymentsReceived":15.00,"asOf":"2026-11-26T12:00:00Z"}
          """);
      stubSales(
          WireMock.get(WireMock.urlPathEqualTo(base + "/" + id + "/statement"))
              .withQueryParam("page", equalTo("0"))
              .withQueryParam("size", equalTo("20")),
          owner,
          company,
          """
          {"invoices":[],"payments":[],"page":0,"size":20,"hasMoreInvoices":false,
           "hasMorePayments":false,"asOf":"2026-11-26T12:00:00Z"}
          """);

      var list = listPage(http, owner, "?search=Cliente&sort=name,asc", company).path("content");
      var all =
          successfulBody(
              get(http, "/api/v1/clients/all?search=Cliente&sort=name,asc", owner, company));
      COMPANY.verify(2, WireMock.postRequestedFor(WireMock.urlPathEqualTo(base + "/balances")));
      assertThat(all).isEqualTo(list);
      assertThat(list.get(0).path("balance").decimalValue()).isEqualByComparingTo("35.00");
      assertThat(list.get(1).path("balance").decimalValue()).isEqualByComparingTo("0.00");

      var summary = successfulBody(get(http, "/api/v1/clients/summary", owner, company));
      assertThat(summary.path("totalClients").asInt()).isEqualTo(2);
      assertThat(summary.path("activeClients").asInt()).isEqualTo(1);
      assertThat(summary.path("clientsWithPendingBalance").asInt()).isEqualTo(1);
      assertThat(summary.path("totalPending").decimalValue()).isEqualByComparingTo("35.00");

      var profile = successfulBody(get(http, detail + "/financial-summary", owner, company));
      assertThat(profile.path("id").asString()).isEqualTo(id);
      assertThat(profile.path("status").asString()).isEqualTo("INACTIVE");
      assertThat(profile.path("availableCredit").isNull()).isTrue();
      assertThat(profile.path("paymentsReceived").decimalValue()).isEqualByComparingTo("15.00");
      assertThat(patch(http, detail, owner, "{\"creditLimit\":\"100.00\"}", company).statusCode())
          .isEqualTo(200);
      profile = successfulBody(get(http, detail + "/financial-summary", owner, company));
      assertThat(profile.path("availableCredit").decimalValue()).isEqualByComparingTo("65.00");

      assertThat(get(http, detail + "/statement", owner, company).statusCode()).isEqualTo(200);
      assertThat(get(http, detail + "/statement", foreign).statusCode()).isEqualTo(404);
    }
  }

  private static void stubSales(
      com.github.tomakehurst.wiremock.client.MappingBuilder request,
      String actor,
      UUID company,
      String response) {
    COMPANY.stubFor(
        request
            .withHeader("Authorization", equalTo("Bearer " + actor))
            .withHeader("X-Company-Id", equalTo(company.toString()))
            .withQueryParam("expectedCompanyId", equalTo(company.toString()))
            .willReturn(
                aResponse().withHeader("Content-Type", "application/json").withBody(response)));
  }

  private String scopedCatalogOwner(UUID tenant) throws Exception {
    var token = session("REGULAR");
    SELECTORS.put(token, tenant);
    stubScopedCatalogMembership(token, tenant, MembershipRole.OWNER, Scope.READ);
    stubScopedCatalogMembership(token, tenant, MembershipRole.OWNER, Scope.WRITE);
    return token;
  }

  private static void stubScopedCatalogMembership(
      String jwt, UUID tenant, MembershipRole role, Scope scope) throws Exception {
    var subject = SignedJWT.parse(jwt).getJWTClaimsSet().getSubject();
    COMPANY.stubFor(
        WireMock.get(urlEqualTo(PATH))
            .withHeader(USER_ASSERTION, equalTo(jwt))
            .andMatching(
                request -> matchesAccessContext(request.getHeader("Authorization"), tenant, scope))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {"userId":"%s","companyId":"%s","module":"CATALOG","scope":"%s",
                         "mode":"MEMBERSHIP","role":"%s","explicitGrant":false}
                        """
                            .formatted(subject, tenant, scope, role))));
  }

  private static MatchResult matchesAccessContext(String authorization, UUID tenant, Scope scope) {
    if (authorization == null || !authorization.startsWith("Bearer ")) {
      return MatchResult.noMatch();
    }
    try {
      var claims = SignedJWT.parse(authorization.substring(7)).getJWTClaimsSet();
      return MatchResult.of(
          tenant.toString().equals(claims.getStringClaim("company_id"))
              && scope.name().equals(claims.getStringClaim("scope")));
    } catch (java.text.ParseException invalid) {
      return MatchResult.noMatch();
    }
  }

  private String verifyClientReferences(HttpClient http, String owner, String path)
      throws Exception {
    var categories = successfulBody(get(http, path + "/categories", owner));
    assertThat(categories.size()).isEqualTo(4);
    String regular =
        categories
            .valueStream()
            .filter(node -> node.path("code").asString().equals("REGULAR"))
            .findFirst()
            .orElseThrow()
            .path("id")
            .asString();
    var references = path + "/reference-data";
    assertThat(successfulBody(get(http, references + "/countries", owner)).size()).isGreaterThan(1);
    assertThat(successfulBody(get(http, references + "/provinces", owner)).size()).isEqualTo(32);
    assertThat(
            successfulBody(get(http, references + "/municipalities?provinceCode=010000", owner))
                .get(0)
                .path("code")
                .asString())
        .isEqualTo("010100");
    assertThat(successfulBody(get(http, references + "/receipt-types", owner)).size()).isEqualTo(4);
    return regular;
  }

  private void verifyClientProfileRejections(
      HttpClient http, String owner, String foreign, String regular, String detailPath)
      throws Exception {
    assertThat(
            postStatus(
                http,
                foreign,
                """
                {"name":"Foreign","clientType":"LEGAL","categoryId":"%s"}
                """
                    .formatted(regular)))
        .isEqualTo(400);
    assertThat(
            postStatus(
                http,
                owner,
                """
                {"name":"Duplicado","clientType":"LEGAL","taxId":"012 345678"}
                """))
        .isEqualTo(409);
    createProfile(
        http,
        foreign,
        """
        {"name":"Otra empresa","clientType":"LEGAL","taxId":"012345678"}
        """);
    assertThat(patch(http, detailPath, owner, "{\"clientType\":\"NATURAL\"}", null).statusCode())
        .isEqualTo(400);
    assertThat(patch(http, detailPath, owner, "{\"provinceCode\":\"020000\"}", null).statusCode())
        .isEqualTo(400);
    assertThat(
            patch(http, detailPath, owner, "{\"preferredReceiptType\":\"UNKNOWN\"}", null)
                .statusCode())
        .isEqualTo(400);
  }

  private JsonNode createProfile(HttpClient http, String token, String payload)
      throws IOException, InterruptedException {
    var response =
        http.send(clientRequest(token, payload, null), HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    return JsonMapper.shared().readTree(response.body());
  }

  private static void await(CountDownLatch latch) {
    try {
      assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new AssertionError(interrupted);
    }
  }

  private HttpResponse<String> patch(
      HttpClient http, String path, String token, String json, UUID companyHeader)
      throws IOException, InterruptedException {
    var request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .header("Content-Type", "application/json")
            .method("PATCH", HttpRequest.BodyPublishers.ofString(json));
    if (token != null) {
      request.header("Authorization", "Bearer " + token);
    }
    if (token != null) {
      request.header(
          "X-Company-Id",
          SELECTORS
              .getOrDefault(token, companyHeader == null ? new UUID(0, 0) : companyHeader)
              .toString());
    }
    return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private static JsonNode successfulBody(HttpResponse<String> response) {
    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    return JsonMapper.shared().readTree(response.body());
  }

  private String createForListing(HttpClient http, String token, String name, String taxId)
      throws Exception {
    var body =
        JsonMapper.shared()
            .createObjectNode()
            .put("name", name)
            .put("taxId", taxId)
            .put("clientType", "LEGAL")
            .put("countryCode", "US");
    var response =
        http.send(
            clientRequest(token, body.toString(), null), HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(201);
    return JsonMapper.shared().readTree(response.body()).path("id").asString();
  }

  private JsonNode listPage(HttpClient http, String token, String query, UUID untrustedCompanyId)
      throws Exception {
    var response = get(http, "/api/v1/clients" + query, token, untrustedCompanyId);
    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    return JsonMapper.shared().readTree(response.body());
  }

  private static void assertPage(
      JsonNode body, int page, int size, long total, int pages, String... ids) {
    assertThat(body.path("page").asInt()).isEqualTo(page);
    assertThat(body.path("size").asInt()).isEqualTo(size);
    assertThat(body.path("totalElements").asLong()).isEqualTo(total);
    assertThat(body.path("totalPages").asInt()).isEqualTo(pages);
    assertThat(body.path("content").isArray()).isTrue();
    assertThat(body.path("content"))
        .extracting(item -> item.path("id").asString())
        .containsExactly(ids);
    assertThat(body.path("content"))
        .allSatisfy(item -> assertThat(item.path("status").asString()).isEqualTo("ACTIVE"));
  }

  private String authorizedToken(UUID companyId) throws Exception {
    return membershipToken(companyId, MembershipRole.OWNER, false);
  }

  private String membershipToken(UUID companyId, MembershipRole role, boolean explicitGrant)
      throws Exception {
    return membershipToken(companyId, role, explicitGrant, Scope.WRITE);
  }

  private String membershipToken(
      UUID companyId, MembershipRole role, boolean explicitGrant, Scope scope) throws Exception {
    var jwt = session("REGULAR");
    stubMembership(jwt, companyId, role, explicitGrant, scope);
    return jwt;
  }

  private static final java.util.Map<String, UUID> SELECTORS =
      new java.util.concurrent.ConcurrentHashMap<>();

  private static void stubMembership(
      String jwt, UUID companyId, MembershipRole role, boolean explicitGrant, Scope scope)
      throws Exception {
    SELECTORS.put(jwt, companyId);
    var subject = SignedJWT.parse(jwt).getJWTClaimsSet().getSubject();
    COMPANY.stubFor(
        WireMock.get(urlEqualTo(PATH))
            .withHeader(USER_ASSERTION, equalTo(jwt))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {"userId":"%s","companyId":"%s","module":"CATALOG","scope":"%s",
                         "mode":"MEMBERSHIP","role":"%s","explicitGrant":%s}
                        """
                            .formatted(subject, companyId, scope, role, explicitGrant))));
  }

  private static void stubFailure(String jwt, int status) {
    COMPANY.stubFor(
        WireMock.get(urlEqualTo(PATH))
            .atPriority(1)
            .withHeader(USER_ASSERTION, equalTo(jwt))
            .willReturn(aResponse().withStatus(status)));
  }

  private static String session(String accountType) throws JOSEException {
    return token(
        KEY, ISSUER, AUDIENCE, Instant.now().plusSeconds(120), UUID.randomUUID(), accountType);
  }

  private static String withTaxId(String taxId) {
    return "{\"name\":\"Client\",\"taxId\":\"%s\",\"clientType\":\"LEGAL\",\"countryCode\":\"US\"}"
        .formatted(taxId);
  }

  private int postStatus(HttpClient http, String token, String body)
      throws IOException, InterruptedException {
    return http.send(clientRequest(token, body, null), HttpResponse.BodyHandlers.ofString())
        .statusCode();
  }

  private HttpRequest clientRequest(String token, String body, UUID untrustedCompanyId) {
    var request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/clients"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));
    if (token != null) {
      request.header("Authorization", "Bearer " + token);
    }
    if (token != null) {
      request.header(
          "X-Company-Id",
          SELECTORS
              .getOrDefault(token, untrustedCompanyId == null ? new UUID(0, 0) : untrustedCompanyId)
              .toString());
    }
    return request.build();
  }

  @Test
  @Tag("smoke")
  void registersSupplierForAuthorizedCompanyAndPersistsNothingForViewer() throws Exception {
    var company = UUID.randomUUID();
    var otherCompany = UUID.randomUUID();
    var forgedId = UUID.randomUUID();
    var owner = authorizedToken(company);
    var viewer = membershipToken(company, MembershipRole.VIEWER, true);
    var body =
        """
        {"name":" Office ","taxId":"  012345678  ","contact":" Laura ","phone":"+1 (809) 555-0118",
         "email":"sales@example.do"}
        """;

    try (var http = HttpClient.newHttpClient()) {
      var created = postSupplier(http, owner, body, otherCompany);
      assertThat(created.statusCode()).isEqualTo(201);
      var response = JsonMapper.shared().readTree(created.body());
      var id = UUID.fromString(response.path("id").asString());
      assertThat(id).isNotEqualTo(forgedId);
      assertThat(response.path("companyId").asString()).isEqualTo(company.toString());
      assertThat(response.path("status").asString()).isEqualTo("ACTIVE");
      assertThat(
              new JdbcTemplate(dataSource)
                  .queryForMap(
                      """
                      SELECT company_id, name, tax_id, contact, phone, email, status
                      FROM catalog.suppliers WHERE id = ?
                      """,
                      id))
          .containsEntry("company_id", company)
          .containsEntry("name", "Office")
          .containsEntry("tax_id", "012345678")
          .containsEntry("contact", "Laura")
          .containsEntry("phone", "+1 (809) 555-0118")
          .containsEntry("email", "sales@example.do")
          .containsEntry("status", "ACTIVE");
      assertThat(postSupplier(http, viewer, SUPPLIER_JSON, otherCompany).statusCode())
          .isEqualTo(403);
      assertThat(supplierCount(company)).isEqualTo(1);
      assertThat(supplierCount(otherCompany)).isZero();
    }
  }

  @Test
  void enforcesSupplierUniquenessOnlyForNonNullTaxIdsInTheSameCompany() throws Exception {
    var company = UUID.randomUUID();
    var other = UUID.randomUUID();
    var a = authorizedToken(company);
    var b = authorizedToken(other);
    var withTax =
        """
        {"name":"Office","contact":"Laura","taxId":"%s"}
        """;

    try (var http = HttpClient.newHttpClient()) {
      assertThat(postSupplier(http, a, withTax.formatted(" 012345679 "), null).statusCode())
          .isEqualTo(201);
      var duplicate = postSupplier(http, a, withTax.formatted("012345679"), null);
      assertThat(duplicate.statusCode()).isEqualTo(409);
      assertThat(JsonMapper.shared().readTree(duplicate.body()).path("errorCode").asString())
          .isEqualTo("DUPLICATE_TAX_ID");
      assertThat(duplicate.body()).doesNotContain("012345679", "uq_suppliers_company_tax_id");
      assertThat(postSupplier(http, b, withTax.formatted("012345679"), null).statusCode())
          .isEqualTo(201);
      assertThat(postSupplier(http, a, SUPPLIER_JSON, null).statusCode()).isEqualTo(201);
      assertThat(postSupplier(http, a, withTax.formatted("   "), null).statusCode()).isEqualTo(201);
      assertThat(supplierCount(company)).isEqualTo(3);
      assertThat(supplierCount(other)).isEqualTo(1);
    }
  }

  @Test
  void validatesSupplierRequestBeforePersisting() throws Exception {
    var company = UUID.randomUUID();
    var owner = authorizedToken(company);
    try (var http = HttpClient.newHttpClient()) {
      for (var body :
          List.of(
              """
              {"name":"   ","contact":"Laura"}
              """,
              """
              {"name":"Office","contact":"Laura","email":"invalid"}
              """,
              """
              {"name":"Office","contact":"Laura","phone":"()--..."}
              """,
              """
              {"name":"Office"}
              """)) {
        var invalid = postSupplier(http, owner, body, null);
        assertThat(invalid.statusCode()).isEqualTo(400);
        assertThat(JsonMapper.shared().readTree(invalid.body()).path("errorCode").asString())
            .isEqualTo("INVALID_REQUEST");
      }
      assertThat(supplierCount(company)).isZero();
    }
  }

  @Test
  void supplierWriteRequiresCurrentMembershipAndFailsClosed() throws Exception {
    var company = UUID.randomUUID();
    var admin = membershipToken(company, MembershipRole.ADMIN, false);
    var ungranted = membershipToken(company, MembershipRole.OPERATOR, false);
    var operator = membershipToken(company, MembershipRole.OPERATOR, true);
    var advisor = session("ADVISOR");
    var unavailable = session("REGULAR");
    var timeout = session("REGULAR");

    stubFailure(advisor, 403);
    stubFailure(unavailable, 503);
    COMPANY.stubFor(
        WireMock.get(urlEqualTo(PATH))
            .withHeader(USER_ASSERTION, equalTo(timeout))
            .willReturn(aResponse().withFixedDelay(3000)));

    try (var http = HttpClient.newHttpClient()) {
      assertThat(postSupplier(http, null, SUPPLIER_JSON, null).statusCode()).isEqualTo(401);
      assertThat(postSupplier(http, "invalid", SUPPLIER_JSON, null).statusCode()).isEqualTo(401);
      assertThat(postSupplier(http, ungranted, SUPPLIER_JSON, null).statusCode()).isEqualTo(403);
      assertThat(postSupplier(http, advisor, SUPPLIER_JSON, company).statusCode()).isEqualTo(403);
      COMPANY.verify(
          0, getRequestedFor(urlEqualTo(PATH)).withHeader(USER_ASSERTION, WireMock.absent()));
      assertThat(postSupplier(http, admin, SUPPLIER_JSON, null).statusCode()).isEqualTo(201);
      assertThat(postSupplier(http, operator, SUPPLIER_JSON, null).statusCode()).isEqualTo(201);
      stubFailure(operator, 403);
      assertThat(postSupplier(http, operator, SUPPLIER_JSON, null).statusCode()).isEqualTo(403);
      for (var bearer : List.of(unavailable, timeout)) {
        var failed = postSupplier(http, bearer, SUPPLIER_JSON, null);
        assertThat(failed.statusCode()).isEqualTo(503);
        assertThat(JsonMapper.shared().readTree(failed.body()).path("errorCode").asString())
            .isEqualTo("AUTHORIZATION_UNAVAILABLE");
      }
      assertThat(supplierCount(company)).isEqualTo(2);
    }
  }

  @Test
  void supplierUseCaseEnforcesMethodSecurityWithoutTheWebFilter() {
    var details = new SupplierDetails("Office", null, "Laura", null, null);
    assertThatThrownBy(() -> createSupplier.execute(details))
        .isInstanceOf(AccessDeniedException.class);
    var id = UUID.randomUUID();
    assertThatThrownBy(() -> listSuppliers.execute(null, null, 0, 20))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> getSupplier.execute(id)).isInstanceOf(AccessDeniedException.class);
    var patch = new SupplierPatch(Map.of(SupplierPatch.Field.NAME, "Updated"));
    assertThatThrownBy(() -> updateSupplier.execute(id, patch))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> changeSupplierStatus.execute(id, SupplierStatus.INACTIVE))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void supplierInsertRollsBackOnUnrelatedPostgresConstraintViolation() {
    var company = UUID.randomUUID();
    var jdbc = new JdbcTemplate(dataSource);
    var details = new SupplierDetails("Office", null, "Laura", null, null);
    var supplier = Supplier.register(company, details);
    var transaction = new TransactionTemplate(transactions);

    assertThatThrownBy(() ->
            transaction.executeWithoutResult(status -> {
              seedSupplier(supplier);
              assertThat(supplierCount(company)).isEqualTo(1);
              jdbc.update(
                  "UPDATE catalog.suppliers SET status = 'UNKNOWN' WHERE id = ?", supplier.id());
            }))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(supplierCount(company)).isZero();

    seedSupplier(supplier);
    assertThatThrownBy(() -> seedSupplier(supplier))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(supplierCount(company)).isEqualTo(1);
  }

  @Test
  @Tag("smoke")
  void managesSuppliersThroughTheHttpLifecycle() throws Exception {
    var company = UUID.randomUUID();
    var owner = authorizedToken(company);
    var viewer = membershipToken(company, MembershipRole.VIEWER, false, Scope.READ);
    var otherCompany = UUID.randomUUID();
    var otherReader = membershipToken(otherCompany, MembershipRole.OWNER, false, Scope.READ);
    try (var http = HttpClient.newHttpClient()) {
      var created =
          postSupplier(
              http,
              owner,
              """
              {"name":"Office North","taxId":"012345680","contact":"Laura","phone":"8095550101"}
              """,
              null);
      assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
      var id = UUID.fromString(JsonMapper.shared().readTree(created.body()).path("id").asString());
      var path = SUPPLIERS + "/" + id;

      assertPage(
          successfulBody(get(http, SUPPLIERS, viewer, otherCompany)), 0, 20, 1, 1, id.toString());
      assertPage(
          successfulBody(get(http, SUPPLIERS + "?search=%20oFFICE%20", viewer)),
          0,
          20,
          1,
          1,
          id.toString());
      var original = successfulBody(get(http, path, viewer));
      assertThat(original.path("contact").asString()).isEqualTo("Laura");
      var edited =
          successfulBody(
              patch(
                  http,
                  path,
                  owner,
                  """
                  {"email":"updated@example.org","phone":null}
                  """,
                  otherCompany));
      assertThat(edited.path("email").asString()).isEqualTo("updated@example.org");
      assertThat(edited.path("phone").isNull()).isTrue();
      assertThat(edited.path("taxId")).isEqualTo(original.path("taxId"));
      assertThat(edited.path("contact")).isEqualTo(original.path("contact"));

      var inactive =
          successfulBody(patch(http, path + "/status", owner, "{\"status\":\"INACTIVE\"}", null));
      assertThat(successfulBody(get(http, path, viewer))).isEqualTo(inactive);
      var page = successfulBody(get(http, SUPPLIERS + "?search=345680&status=inactive", viewer));
      assertThat(page.path("totalElements").asLong()).isEqualTo(1);
      assertThat(page.path("content").get(0).path("id").asString()).isEqualTo(id.toString());
      assertThat(page.path("content").get(0).path("status").asString()).isEqualTo("INACTIVE");

      var jdbc = new JdbcTemplate(dataSource);
      var revision =
          jdbc.queryForObject(
              "SELECT revision FROM catalog.suppliers WHERE id = ?", Long.class, id);
      assertThat(
              successfulBody(
                  patch(http, path + "/status", owner, "{\"status\":\"INACTIVE\"}", null)))
          .isEqualTo(inactive);
      assertThat(
              jdbc.queryForObject(
                  "SELECT revision FROM catalog.suppliers WHERE id = ?", Long.class, id))
          .isEqualTo(revision);
      assertThat(
              successfulBody(patch(http, path + "/status", owner, "{\"status\":\"ACTIVE\"}", null)))
          .isEqualTo(edited);
      assertThat(successfulBody(get(http, path, viewer))).isEqualTo(edited);
      assertThat(get(http, path, otherReader, company).statusCode()).isEqualTo(404);
      assertThat(
              jdbc.queryForObject(
                  "SELECT company_id FROM catalog.suppliers WHERE id = ?", UUID.class, id))
          .isEqualTo(company);
    }
  }

  @Test
  @Tag("regression")
  void pagesSuppliersInStableOrderAndEscapesLiteralFilters() throws Exception {
    var tenant = UUID.randomUUID();
    var reader = membershipToken(tenant, MembershipRole.OWNER, false, Scope.READ);
    var original = new SupplierDetails("Beta 100%_!", "AZ-123", "Hidden Contact", null, null);
    var first =
        new Supplier(
            UUID.fromString("00000000-0000-4000-8000-000000000001"),
            tenant,
            new SupplierDetails("Beta 100%_!", null, "Contact", null, null),
            SupplierStatus.ACTIVE);
    var second =
        new Supplier(
            UUID.fromString("00000000-0000-4000-8000-000000000002"),
            tenant,
            original,
            SupplierStatus.ACTIVE);
    var alpha =
        Supplier.register(tenant, new SupplierDetails("Alpha", null, "Contact", null, null));
    seedSupplier(second);
    seedSupplier(first);
    seedSupplier(alpha);
    seedSupplier(Supplier.register(UUID.randomUUID(), original));

    try (var http = HttpClient.newHttpClient()) {
      assertPage(
          successfulBody(get(http, SUPPLIERS + "?size=2&page=0", reader)),
          0,
          2,
          3,
          2,
          alpha.id().toString(),
          first.id().toString());
      assertPage(
          successfulBody(get(http, SUPPLIERS + "?size=2&page=1", reader)),
          1,
          2,
          3,
          2,
          second.id().toString());
      assertPage(
          successfulBody(get(http, SUPPLIERS + "?search=%25_%21&size=1&page=1", reader)),
          1,
          1,
          2,
          2,
          second.id().toString());
      assertPage(
          successfulBody(get(http, SUPPLIERS + "?search=Az-12&status=ACTIVE", reader)),
          0,
          20,
          1,
          1,
          second.id().toString());
      assertPage(
          successfulBody(get(http, SUPPLIERS + "?search=Hidden%20Contact", reader)),
          0,
          20,
          1,
          1,
          second.id().toString());
      assertPage(successfulBody(get(http, SUPPLIERS + "?status=INACTIVE", reader)), 0, 20, 0, 0);
      assertPage(
          successfulBody(get(http, SUPPLIERS + "?size=2&page=2147483647", reader)),
          Integer.MAX_VALUE,
          2,
          3,
          2);
    }
  }

  @Test
  @Tag("regression")
  void checksCurrentSupplierPermissionsForReadsAndBothPatches() throws Exception {
    var tenant = UUID.randomUUID();
    var otherTenant = UUID.randomUUID();
    var admin = membershipToken(tenant, MembershipRole.ADMIN, false);
    var viewer = membershipToken(tenant, MembershipRole.VIEWER, false);
    var reader = membershipToken(tenant, MembershipRole.VIEWER, false, Scope.READ);
    var operator = membershipToken(tenant, MembershipRole.OPERATOR, false);
    var grantedOperator = membershipToken(tenant, MembershipRole.OPERATOR, true);
    var otherWriter = authorizedToken(otherTenant);
    var advisor = session("ADVISOR");
    stubFailure(advisor, 403);
    var advisorWithRead = session("ADVISOR");
    stubFailure(advisorWithRead, 403);
    var advisorId = SignedJWT.parse(advisorWithRead).getJWTClaimsSet().getSubject();
    COMPANY.stubFor(
        WireMock.get(urlEqualTo(PATH))
            .withHeader(USER_ASSERTION, WireMock.absent())
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {"userId":"%s","companyId":"%s","module":"CATALOG","scope":"READ","mode":"DELEGATED"}
                        """
                            .formatted(advisorId, tenant))));
    var timeout = session("REGULAR");
    COMPANY.stubFor(
        WireMock.get(urlEqualTo(PATH))
            .withHeader(USER_ASSERTION, equalTo(timeout))
            .willReturn(aResponse().withFixedDelay(3000)));
    var supplier =
        Supplier.register(tenant, new SupplierDetails("Protected", null, "Laura", null, null));
    seedSupplier(supplier);
    var path = SUPPLIERS + "/" + supplier.id();
    var inactive = "{\"status\":\"INACTIVE\"}";

    try (var http = HttpClient.newHttpClient()) {
      assertThat(get(http, SUPPLIERS, null).statusCode()).isEqualTo(401);
      assertThat(get(http, path, "invalid").statusCode()).isEqualTo(401);
      assertThat(patch(http, path, null, "{\"name\":\"Denied\"}", null).statusCode())
          .isEqualTo(401);
      assertThat(patch(http, path + "/status", "invalid", inactive, null).statusCode())
          .isEqualTo(401);
      var allowed = successfulBody(patch(http, path, admin, "{\"name\":\"Allowed\"}", otherTenant));
      for (String denied : List.of(viewer, operator, advisor, advisorWithRead)) {
        assertThat(patch(http, path, denied, "{\"name\":\"Denied\"}", tenant).statusCode())
            .isEqualTo(403);
        assertThat(patch(http, path + "/status", denied, inactive, tenant).statusCode())
            .isEqualTo(403);
      }
      assertThat(get(http, path, advisor, tenant).statusCode()).isEqualTo(403);
      COMPANY.verify(
          0, getRequestedFor(urlEqualTo(PATH)).withHeader(USER_ASSERTION, WireMock.absent()));
      assertThat(patch(http, path, otherWriter, "{\"name\":\"Denied\"}", tenant).statusCode())
          .isEqualTo(404);
      assertThat(patch(http, path + "/status", otherWriter, inactive, tenant).statusCode())
          .isEqualTo(404);
      assertThat(successfulBody(get(http, path, reader))).isEqualTo(allowed);
      var changed = successfulBody(patch(http, path + "/status", grantedOperator, inactive, null));
      assertThat(changed.path("status").asString()).isEqualTo("INACTIVE");
      stubFailure(grantedOperator, 403);
      assertThat(
              patch(http, path + "/status", grantedOperator, "{\"status\":\"ACTIVE\"}", null)
                  .statusCode())
          .isEqualTo(403);
      assertThat(patch(http, path, timeout, "{\"name\":\"Denied\"}", null).statusCode())
          .isEqualTo(503);
      assertThat(successfulBody(get(http, path, reader))).isEqualTo(changed);
    }
  }

  @Test
  @Tag("regression")
  void handlesSupplierDuplicateTaxIdAndConflictingWritesInPostgres() throws Exception {
    var tenant = UUID.randomUUID();
    var writer = authorizedToken(tenant);
    var first =
        Supplier.register(tenant, new SupplierDetails("First", "012345690", "Laura", null, null));
    var second =
        Supplier.register(tenant, new SupplierDetails("Second", null, "Laura", null, null));
    seedSupplier(first.changeStatus(SupplierStatus.INACTIVE));
    seedSupplier(second);
    try (var http = HttpClient.newHttpClient()) {
      var duplicate =
          patch(http, SUPPLIERS + "/" + second.id(), writer, "{\"taxId\":\" 012345690 \"}", null);
      assertThat(duplicate.statusCode()).isEqualTo(409);
      assertThat(JsonMapper.shared().readTree(duplicate.body()).path("errorCode").asString())
          .isEqualTo("DUPLICATE_TAX_ID");
      assertThat(suppliers.find(tenant, second.id())).contains(completeSupplier(second));
    }

    var read = new CountDownLatch(1);
    var committed = new CountDownLatch(1);
    try (var executor = Executors.newSingleThreadExecutor()) {
      var stale =
          executor.submit(() ->
              suppliers.update(
                  tenant,
                  second.id(),
                  current -> {
                    read.countDown();
                    await(committed);
                    return current.changeStatus(SupplierStatus.INACTIVE);
                  }));
      try {
        await(read);
        suppliers.update(
            tenant,
            second.id(),
            current ->
                current.updateDetails(
                    new SupplierPatch(Map.of(SupplierPatch.Field.EMAIL, "saved@example.org"))));
      } finally {
        committed.countDown();
      }
      assertThatThrownBy(() -> stale.get(10, TimeUnit.SECONDS))
          .hasCauseInstanceOf(SupplierAccessException.class)
          .hasRootCauseMessage("SUPPLIER_UPDATE_CONFLICT");
    }
    assertThat(suppliers.find(tenant, second.id()))
        .hasValueSatisfying(saved -> {
          assertThat(saved.status()).isEqualTo(SupplierStatus.ACTIVE);
          assertThat(saved.details().email()).isEqualTo("saved@example.org");
        });
  }

  @Test
  @Tag("smoke")
  void managesSupplierOffersAndTransfersPrimaryWithoutChangingSalePrice() throws Exception {
    var company = UUID.randomUUID();
    var owner = scopedCatalogOwner(company);
    var otherOwner = scopedCatalogOwner(UUID.randomUUID());
    var viewer = session("REGULAR");
    SELECTORS.put(viewer, company);
    stubScopedCatalogMembership(viewer, company, MembershipRole.VIEWER, Scope.READ);
    stubScopedCatalogMembership(viewer, company, MembershipRole.VIEWER, Scope.WRITE);
    try (var http = HttpClient.newHttpClient()) {
      var categories = successfulBody(get(http, SUPPLIERS + "/categories", owner));
      assertThat(categories.size()).isEqualTo(7);
      var category = categories.get(0).path("id").asString();
      var a = createCommercialSupplier(http, owner, "A", category);
      var b = createCommercialSupplier(http, owner, "B", category);
      var item = createdId(postItem(http, owner, PRODUCT_ITEM, null));
      var foreignItem = createdId(postItem(http, otherOwner, PRODUCT_ITEM, null));
      var linksA = SUPPLIERS + "/" + a + "/items";
      var linksB = SUPPLIERS + "/" + b + "/items";
      var inverse = CATALOG_ITEMS + "/" + item + "/suppliers";

      assertThat(putSupplier(http, linksA, viewer, offer(item, true, false)).statusCode())
          .isEqualTo(403);
      assertThat(putSupplier(http, linksA, owner, offer(item, true, false)).statusCode())
          .isEqualTo(204);
      var forward = successfulBody(get(http, linksA, owner)).path("content").get(0);
      assertThat(forward.path("catalogItemId").asString()).isEqualTo(item);
      assertThat(forward.path("offeredCost").asString()).isEqualTo("25.1234");
      assertThat(forward.path("primary").asBoolean()).isTrue();
      var itemDetail = successfulBody(get(http, CATALOG_ITEMS + "/" + item, owner));
      assertThat(itemDetail.path("primarySupplierId").asString()).isEqualTo(a);
      assertThat(itemDetail.path("primarySupplierName").asString()).isEqualTo("A");
      assertThat(itemDetail.path("unitDivisible").isBoolean()).isTrue();
      assertThat(putSupplier(http, linksB, owner, offer(item, false, false)).statusCode())
          .isEqualTo(204);
      assertThat(
              successfulBody(get(http, inverse + "?size=1&page=1", owner))
                  .path("content")
                  .get(0)
                  .path("supplierId")
                  .asString())
          .isEqualTo(b);
      assertThat(putSupplier(http, linksB, owner, offer(item, true, false)).statusCode())
          .isEqualTo(409);
      assertThat(putSupplier(http, linksB, owner, offer(item, true, true)).statusCode())
          .isEqualTo(204);
      var beforeRetry = successfulBody(get(http, inverse, owner));
      assertThat(beforeRetry.path("content").get(0).path("primary").asBoolean()).isFalse();
      assertThat(beforeRetry.path("content").get(1).path("primary").asBoolean()).isTrue();
      assertThat(putSupplier(http, linksB, owner, offer(item, true, true)).statusCode())
          .isEqualTo(204);
      assertThat(successfulBody(get(http, inverse, owner))).isEqualTo(beforeRetry);

      assertThat(
              patch(http, SUPPLIERS + "/" + b + "/status", owner, "{\"status\":\"INACTIVE\"}", null)
                  .statusCode())
          .isEqualTo(200);
      var history =
          successfulBody(get(http, linksB + "?status=INACTIVE", owner)).path("content").get(0);
      assertThat(history.path("primary").asBoolean()).isFalse();
      assertThat(history.path("validUntil").isString()).isTrue();
      assertThat(
              patch(http, SUPPLIERS + "/" + b + "/status", owner, "{\"status\":\"ACTIVE\"}", null)
                  .statusCode())
          .isEqualTo(200);
      assertThat(
              successfulBody(get(http, linksB + "?status=ACTIVE", owner))
                  .path("totalElements")
                  .asLong())
          .isZero();
      assertThat(putSupplier(http, linksA, owner, offer(foreignItem, true, false)).statusCode())
          .isEqualTo(404);
      assertThat(successfulBody(get(http, linksA, owner)).path("totalElements").asLong())
          .isEqualTo(1);
      assertThat(get(http, inverse, otherOwner).statusCode()).isEqualTo(404);
      assertThat(
              new JdbcTemplate(dataSource)
                  .queryForObject(
                      "SELECT price FROM catalog.catalog_items WHERE id = ?",
                      BigDecimal.class,
                      UUID.fromString(item)))
          .isEqualByComparingTo("3200.00");
    }
  }

  @Test
  @Tag("regression")
  void roundTripsTypedProfileSearchAndTenantCategoryReferences() throws Exception {
    var owner = scopedCatalogOwner(UUID.randomUUID());
    var otherOwner = scopedCatalogOwner(UUID.randomUUID());
    try (var http = HttpClient.newHttpClient()) {
      var category =
          successfulBody(get(http, SUPPLIERS + "/categories", owner)).get(0).path("id").asString();
      var foreign =
          successfulBody(get(http, SUPPLIERS + "/categories", otherOwner))
              .get(0)
              .path("id")
              .asString();
      var created =
          postSupplier(
              http,
              owner,
              """
              {"name":"Distribución","contact":"Alex 100%%_!","taxId":"001-0000001-2",
               "email":"ventas@example.do","phone":"8095550100","address":"Santo Domingo",
               "supplyType":"BOTH","paymentTerms":"BOTH","creditDays":0,"categoryId":"%s",
               "observations":"Retirar"}
              """
                  .formatted(category),
              null);
      var id = createdId(created);
      var path = SUPPLIERS + "/" + id;
      var detail = successfulBody(get(http, path, owner));
      assertThat(detail.path("taxId").asString()).isEqualTo("00100000012");
      assertThat(detail.path("creditDays").asInt()).isZero();
      assertThat(detail.path("address").asString()).isEqualTo("Santo Domingo");
      assertThat(
              successfulBody(
                      get(
                          http,
                          SUPPLIERS
                              + "?search=alex%20100%25_%21"
                              + "&supplyType=PRODUCT&paymentTerms=CREDIT&status=ACTIVE&sort=name,desc&categoryId="
                              + category,
                          owner))
                  .path("content")
                  .get(0)
                  .path("id")
                  .asString())
          .isEqualTo(id);
      assertThat(
              patch(http, path, owner, "{\"categoryId\":\"%s\"}".formatted(foreign), null)
                  .statusCode())
          .isEqualTo(400);
      assertThat(patch(http, path, owner, "{\"paymentTerms\":\"CASH\"}", null).statusCode())
          .isEqualTo(400);
      var edited =
          successfulBody(
              patch(
                  http,
                  path,
                  owner,
                  """
                  {"categoryId":null,"paymentTerms":"CREDIT","creditDays":61,"observations":null}
                  """,
                  null));
      assertThat(edited.path("creditDays").asInt()).isEqualTo(61);
      assertThat(edited.path("categoryId").isNull()).isTrue();
      assertThat(edited.path("observations").isNull()).isTrue();
      assertThat(edited.path("supplyType").asString()).isEqualTo("BOTH");
    }
  }

  @Test
  @Tag("regression")
  void retiresOmittedLinksAndRejectsIncompatibleReplacementAtomically() throws Exception {
    var owner = scopedCatalogOwner(UUID.randomUUID());
    try (var http = HttpClient.newHttpClient()) {
      var supplier = createCommercialSupplier(http, owner, "A", null);
      var product = createdId(postItem(http, owner, PRODUCT_ITEM, null));
      var service = createdId(postItem(http, owner, SERVICE_ITEM, null));
      var path = SUPPLIERS + "/" + supplier;
      var links = path + "/items";
      var both =
          """
          {"items":[{"catalogItemId":"%s","primary":true},{"catalogItemId":"%s","offeredCost":"0"}]}
          """
              .formatted(product, service);
      assertThat(putSupplier(http, links, owner, both).statusCode()).isEqualTo(204);
      assertThat(patch(http, path, owner, "{\"supplyType\":\"PRODUCT\"}", null).statusCode())
          .isEqualTo(409);
      var invalid = both.replace(service, UUID.randomUUID().toString());
      assertThat(putSupplier(http, links, owner, invalid).statusCode()).isEqualTo(404);
      assertThat(
              successfulBody(get(http, links + "?status=ACTIVE", owner))
                  .path("totalElements")
                  .asInt())
          .isEqualTo(2);
      assertThat(
              patch(
                      http,
                      CATALOG_ITEMS + "/" + product + "/status",
                      owner,
                      "{\"active\":false}",
                      null)
                  .statusCode())
          .isEqualTo(200);
      assertThat(putSupplier(http, links, owner, "{\"items\":[]}").statusCode()).isEqualTo(204);
      var history = successfulBody(get(http, links + "?status=INACTIVE", owner));
      assertThat(history.path("content").size()).isEqualTo(2);
      assertThat(history.path("content"))
          .allSatisfy(row -> {
            assertThat(row.path("primary").asBoolean()).isFalse();
            assertThat(row.path("validUntil").isString()).isTrue();
          });
    }
  }

  @Test
  @Tag("regression")
  void serializesConcurrentSupplierWritesAndPreservesUniquePrimary() throws Exception {
    var tenant = UUID.randomUUID();
    var owner = scopedCatalogOwner(tenant);
    var jdbc = new JdbcTemplate(dataSource);
    try (var http = HttpClient.newHttpClient()) {
      var categoriesPath = SUPPLIERS + "/categories";
      assertThat(
              race(
                  () -> get(http, categoriesPath, owner).statusCode(),
                  () -> get(http, categoriesPath, owner).statusCode()))
          .containsExactly(200, 200);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM catalog.supplier_categories WHERE company_id = ?",
                  Integer.class,
                  tenant))
          .isEqualTo(7);
      String tax = "{\"name\":\"Fiscal\",\"contact\":\"Alex\",\"taxId\":\"00100000012\"}";
      assertThat(
              race(
                  () -> postSupplier(http, owner, tax, null).statusCode(),
                  () -> postSupplier(http, owner, tax, null).statusCode()))
          .containsExactlyInAnyOrder(201, 409);
      assertThat(supplierCount(tenant)).isEqualTo(1);

      var a = createCommercialSupplier(http, owner, "A", null);
      var b = createCommercialSupplier(http, owner, "B", null);
      var product = createdId(postItem(http, owner, PRODUCT_ITEM, null));
      var service = createdId(postItem(http, owner, SERVICE_ITEM, null));
      var linksA = SUPPLIERS + "/" + a + "/items";
      var linksB = SUPPLIERS + "/" + b + "/items";
      var same = offer(product, false, false);
      assertThat(
              race(
                  () -> putSupplier(http, linksA, owner, same).statusCode(),
                  () -> putSupplier(http, linksA, owner, same).statusCode()))
          .containsExactly(204, 204);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM catalog.supplier_items WHERE company_id = ?",
                  Integer.class,
                  tenant))
          .isEqualTo(1);
      assertThat(
              race(
                  () -> putSupplier(http, linksA, owner, offer(product, true, false)).statusCode(),
                  () -> putSupplier(http, linksB, owner, offer(product, true, false)).statusCode()))
          .containsExactlyInAnyOrder(204, 409);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM catalog.supplier_items WHERE company_id = ? AND is_primary",
                  Integer.class,
                  tenant))
          .isEqualTo(1);
      assertThat(
              race(
                  () -> putSupplier(http, linksA, owner, offer(service, false, false)).statusCode(),
                  () -> putSupplier(http, linksA, owner, same).statusCode()))
          .containsExactly(204, 204);
      assertThat(
              successfulBody(get(http, linksA + "?status=ACTIVE", owner))
                  .path("totalElements")
                  .asInt())
          .isEqualTo(1);
      var supplierStatus = SUPPLIERS + "/" + b + "/status";
      var supplierRace =
          race(
              () -> putSupplier(http, linksB, owner, offer(product, true, true)).statusCode(),
              () ->
                  patch(http, supplierStatus, owner, "{\"status\":\"INACTIVE\"}", null)
                      .statusCode());
      assertThat(supplierRace.get(0)).isIn(204, 409);
      assertThat(supplierRace.get(1)).isEqualTo(200);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM catalog.supplier_items WHERE supplier_id = ? AND valid_until IS NULL",
                  Integer.class,
                  UUID.fromString(b)))
          .isZero();
      var itemRace =
          race(
              () -> putSupplier(http, linksA, owner, offer(service, true, true)).statusCode(),
              () ->
                  patch(
                          http,
                          CATALOG_ITEMS + "/" + service + "/status",
                          owner,
                          "{\"active\":false}",
                          null)
                      .statusCode());
      assertThat(itemRace.get(0)).isIn(204, 409);
      assertThat(itemRace.get(1)).isEqualTo(200);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM catalog.supplier_items WHERE catalog_item_id = ? AND valid_until IS NULL",
                  Integer.class,
                  UUID.fromString(service)))
          .isZero();
    }
  }

  @Test
  @Tag("regression")
  void enforcesSupplierLinkTenantKeysAndRechecksAuthorizationForTheSameActor() throws Exception {
    var companyA = UUID.randomUUID();
    var companyB = UUID.randomUUID();
    var owner = scopedCatalogOwner(companyA);
    var foreignOwner = scopedCatalogOwner(companyB);
    var actor = session("REGULAR");
    stubScopedCatalogMembership(actor, companyA, MembershipRole.OWNER, Scope.READ);
    stubScopedCatalogMembership(actor, companyA, MembershipRole.OWNER, Scope.WRITE);
    stubScopedCatalogMembership(actor, companyB, MembershipRole.VIEWER, Scope.READ);
    stubScopedCatalogMembership(actor, companyB, MembershipRole.VIEWER, Scope.WRITE);
    try (var http = HttpClient.newHttpClient()) {
      var supplier = createCommercialSupplier(http, owner, "A", null);
      var domestic = createdId(postItem(http, owner, PRODUCT_ITEM, null));
      var foreign = createdId(postItem(http, foreignOwner, PRODUCT_ITEM, null));
      var links = SUPPLIERS + "/" + supplier + "/items";
      var body = offer(domestic, true, false);
      assertThat(putSupplier(http, links, actor, body, companyA).statusCode()).isEqualTo(204);
      assertThat(putSupplier(http, links, actor, body, companyB).statusCode()).isEqualTo(403);
      assertThat(get(http, links, actor, companyB).statusCode()).isEqualTo(404);
      assertThat(get(http, links, actor, companyA).statusCode()).isEqualTo(200);
      stubFailure(actor, 403);
      assertThat(putSupplier(http, links, actor, body, companyA).statusCode()).isEqualTo(403);
      stubFailure(actor, 503);
      assertThat(get(http, links, actor, companyA).statusCode()).isEqualTo(503);

      var jdbc = new JdbcTemplate(dataSource);
      var supplierId = UUID.fromString(supplier);
      var itemId = UUID.fromString(domestic);
      var insert =
          "INSERT INTO catalog.supplier_items (company_id, supplier_id, catalog_item_id, is_primary)"
              + " VALUES (?, ?, ?, true)";
      assertThatThrownBy(() -> jdbc.update(insert, companyA, supplierId, UUID.fromString(foreign)))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThatThrownBy(() -> jdbc.update(insert, companyB, supplierId, UUID.fromString(foreign)))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThatThrownBy(() -> jdbc.update(insert, companyA, supplierId, itemId))
          .isInstanceOf(DataIntegrityViolationException.class);
      var another = UUID.fromString(createCommercialSupplier(http, owner, "Another", null));
      assertThatThrownBy(() -> jdbc.update(insert, companyA, another, itemId))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThat(successfulBody(get(http, links, owner)).path("totalElements").asInt())
          .isEqualTo(1);
    }
  }

  private static List<Integer> race(
      java.util.concurrent.Callable<Integer> first, java.util.concurrent.Callable<Integer> second)
      throws Exception {
    var gate = new java.util.concurrent.CyclicBarrier(2);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var a =
          executor.submit(() -> {
            gate.await(10, TimeUnit.SECONDS);
            return first.call();
          });
      var b =
          executor.submit(() -> {
            gate.await(10, TimeUnit.SECONDS);
            return second.call();
          });
      return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
    }
  }

  private String createCommercialSupplier(
      HttpClient http, String token, String name, String category) throws Exception {
    var body =
        JsonMapper.shared()
            .createObjectNode()
            .put("name", name)
            .put("contact", "Alex")
            .put("supplyType", "BOTH");
    if (category != null) {
      body.put("categoryId", category);
    }
    return createdId(postSupplier(http, token, body.toString(), null));
  }

  private static String createdId(HttpResponse<String> response) {
    assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    return JsonMapper.shared().readTree(response.body()).path("id").asString();
  }

  private static String offer(String item, boolean primary, boolean replace) {
    return """
    {"replacePrimary":%s,"items":[{"catalogItemId":"%s","offeredCost":"25.1234",
     "supplierReference":"REF-1","primary":%s}]}
    """
        .formatted(replace, item, primary);
  }

  private HttpResponse<String> putSupplier(HttpClient http, String path, String token, String json)
      throws Exception {
    return putSupplier(http, path, token, json, SELECTORS.get(token));
  }

  private HttpResponse<String> putSupplier(
      HttpClient http, String path, String token, String json, UUID company) throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .timeout(java.time.Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + token)
            .header("X-Company-Id", company.toString())
            .PUT(HttpRequest.BodyPublishers.ofString(json))
            .build();
    return http.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private int supplierCount(UUID company) {
    return new JdbcTemplate(dataSource)
        .queryForObject(
            "SELECT count(*) FROM catalog.suppliers WHERE company_id = ?", Integer.class, company);
  }

  private HttpResponse<String> postSupplier(
      HttpClient http, String bearer, String body, UUID forgedCompany) throws Exception {
    var suffix = forgedCompany == null ? "" : "?companyId=" + forgedCompany;
    var request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + SUPPLIERS + suffix))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));
    if (bearer != null) {
      request.header("Authorization", "Bearer " + bearer);
    }
    if (bearer != null) {
      request.header(
          "X-Company-Id",
          SELECTORS
              .getOrDefault(bearer, forgedCompany == null ? new UUID(0, 0) : forgedCompany)
              .toString());
    }
    return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  @Test
  @Tag("smoke")
  void registersBothCatalogItemTypesWithExactDopPricesForTheAuthorizedCompany() throws Exception {
    var companyA = UUID.randomUUID();
    var companyB = UUID.randomUUID();
    var owner = authorizedToken(companyA);
    var admin = membershipToken(companyB, MembershipRole.ADMIN, false);
    var viewer = membershipToken(companyA, MembershipRole.VIEWER, false);
    try (var http = HttpClient.newHttpClient()) {
      var service = postItem(http, owner, SERVICE_ITEM, companyB);
      var product = postItem(http, owner, PRODUCT_ITEM, null);
      var other = postItem(http, admin, PRODUCT_ITEM, companyA);
      assertPersistedItem(service, companyA, "Consultoría", "SERVICE", "15000.00");
      assertPersistedItem(product, companyA, "Silla", "PRODUCT", "3200.00");
      assertPersistedItem(other, companyB, "Silla", "PRODUCT", "3200.00");

      for (var input :
          List.of(
              SERVICE_ITEM.replace("15000.00", "-0.01"),
              SERVICE_ITEM.replace("15000.00", "10.999"),
              SERVICE_ITEM.replace("15000.00", "1000000000000"),
              SERVICE_ITEM.replace("15000.00", "null"),
              SERVICE_ITEM.replace("15000.00", "\"invalid\""),
              SERVICE_ITEM.replace("SERVICE", "OTHER"),
              SERVICE_ITEM.replace("Consultoría", "  "),
              SERVICE_ITEM.replace("\"name\"", "\"currency\":\"USD\",\"name\""),
              SERVICE_ITEM.replace("\"name\"", "\"companyId\":\"" + companyB + "\",\"name\""))) {
        var rejected = postItem(http, owner, input, null);
        assertThat(rejected.statusCode()).as(rejected.body()).isEqualTo(400);
        assertThat(JsonMapper.shared().readTree(rejected.body()).path("errorCode").asString())
            .isEqualTo("INVALID_REQUEST");
      }
      assertThat(postItem(http, viewer, SERVICE_ITEM, null).statusCode()).isEqualTo(403);
      assertThat(itemCount(companyA)).isEqualTo(2);
      assertThat(itemCount(companyB)).isEqualTo(1);
    }
  }

  @Test
  @Tag("regression")
  void catalogItemWriteRechecksCompanyPermissionsAndFailsClosed() throws Exception {
    var company = UUID.randomUUID();
    var ungranted = membershipToken(company, MembershipRole.OPERATOR, false);
    var operator = membershipToken(company, MembershipRole.OPERATOR, true);
    var advisor = session("ADVISOR");
    var timeout = session("REGULAR");
    var unavailable = session("REGULAR");
    stubFailure(advisor, 403);
    stubFailure(unavailable, 503);
    COMPANY.stubFor(
        WireMock.get(urlEqualTo(PATH))
            .withHeader(USER_ASSERTION, equalTo(timeout))
            .willReturn(aResponse().withFixedDelay(3000)));

    try (var http = HttpClient.newHttpClient()) {
      assertThat(postItem(http, null, PRODUCT_ITEM, null).statusCode()).isEqualTo(401);
      assertThat(postItem(http, "invalid", PRODUCT_ITEM, null).statusCode()).isEqualTo(401);
      assertThat(postItem(http, ungranted, PRODUCT_ITEM, null).statusCode()).isEqualTo(403);
      assertThat(postItem(http, advisor, PRODUCT_ITEM, company).statusCode()).isEqualTo(403);
      assertThat(postItem(http, operator, PRODUCT_ITEM, null).statusCode()).isEqualTo(201);
      stubFailure(operator, 403);
      assertThat(postItem(http, operator, PRODUCT_ITEM, null).statusCode()).isEqualTo(403);
      for (var token : List.of(timeout, unavailable)) {
        var rejected = postItem(http, token, PRODUCT_ITEM, null);
        assertThat(rejected.statusCode()).isEqualTo(503);
        assertThat(JsonMapper.shared().readTree(rejected.body()).path("errorCode").asString())
            .isEqualTo("AUTHORIZATION_UNAVAILABLE");
      }
      assertThat(itemCount(company)).isEqualTo(1);
    }
  }

  @Test
  void catalogItemUseCaseRequiresAuthenticationOutsideTheWebFilter() {
    assertThatThrownBy(() ->
            createCatalogItem.execute(
                "Chair", CatalogItemType.PRODUCT, BigDecimal.ONE, null, CatalogItemProfile.LEGACY))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @Tag("regression")
  void postgresEnforcesCatalogItemConstraints() {
    var company = UUID.randomUUID();
    var jdbc = new JdbcTemplate(dataSource);
    for (var invalid :
        List.of(
            List.of("Chair", "PRODUCT", "-0.01", "DOP"),
            List.of("Chair", "PRODUCT", "1000000000000000", "DOP"),
            List.of("Chair", "PRODUCT", "NaN", "DOP"),
            List.of("Chair", "OTHER", "0", "DOP"),
            List.of("Chair", "SERVICE", "0", "USD"),
            List.of(" \t ", "PRODUCT", "0", "DOP"))) {
      assertThatThrownBy(() ->
              jdbc.update(
                  """
                  INSERT INTO catalog.catalog_items (id, company_id, name, type, price, currency)
                  VALUES (?, ?, ?, ?, CAST(? AS NUMERIC), ?)
                  """,
                  UUID.randomUUID(),
                  company,
                  invalid.get(0),
                  invalid.get(1),
                  invalid.get(2),
                  invalid.get(3)))
          .isInstanceOf(DataIntegrityViolationException.class);
    }
    assertThat(itemCount(company)).isZero();
  }

  @Test
  @Tag("smoke")
  void managesExistingCatalogItemsThroughHttp() throws Exception {
    var tenant = UUID.randomUUID();
    var otherTenant = UUID.randomUUID();
    var owner = authorizedToken(tenant);
    var reader = membershipToken(tenant, MembershipRole.VIEWER, false, Scope.READ);
    var outsider = authorizedToken(otherTenant);
    var outsiderReader = membershipToken(otherTenant, MembershipRole.VIEWER, false, Scope.READ);
    try (var http = HttpClient.newHttpClient()) {
      var service = postItem(http, owner, SERVICE_ITEM, null);
      var product = postItem(http, owner, PRODUCT_ITEM, null);
      assertPersistedItem(service, tenant, "Consultoría", "SERVICE", "15000.00");
      assertPersistedItem(product, tenant, "Silla", "PRODUCT", "3200.00");
      var id = UUID.fromString(JsonMapper.shared().readTree(product.body()).path("id").asString());
      var path = CATALOG_ITEMS + "/" + id;
      var listed = successfulBody(get(http, CATALOG_ITEMS, reader));
      assertThat(listed.path("totalElements").asLong()).isEqualTo(2);
      assertThat(
              successfulBody(get(http, CATALOG_ITEMS + "?type=SERVICE", reader))
                  .path("content")
                  .get(0)
                  .path("type")
                  .asString())
          .isEqualTo("SERVICE");
      assertThat(
              successfulBody(get(http, CATALOG_ITEMS + "?search=%20sIL%20", reader))
                  .path("content")
                  .get(0)
                  .path("id")
                  .asString())
          .isEqualTo(id.toString());
      var detail = successfulBody(get(http, path, reader));
      assertThat(detail.path("name").asString()).isEqualTo("Silla");
      assertThat(detail.has("revision")).isFalse();
      assertThat(detail.has("companyId")).isFalse();

      var edited =
          patch(http, path, owner, "{\"description\":\"Silla gris\",\"price\":3500.00}", null);
      assertThat(edited.body()).contains("\"price\":3500.00");
      var updated = successfulBody(edited);
      assertThat(updated.path("description").asString()).isEqualTo("Silla gris");
      assertThat(updated.path("currency").asString()).isEqualTo("DOP");
      assertThat(successfulBody(get(http, path, reader))).isEqualTo(updated);

      var inactive =
          successfulBody(patch(http, path + "/status", owner, "{\"active\":false}", null));
      assertThat(inactive.path("active").asBoolean()).isFalse();
      assertThat(successfulBody(get(http, path, reader))).isEqualTo(inactive);
      assertThat(
              successfulBody(
                      get(http, CATALOG_ITEMS + "?type=PRODUCT&search=sil&active=false", reader))
                  .path("content")
                  .get(0))
          .isEqualTo(inactive);
      var revision = itemRevision(id);
      assertThat(successfulBody(patch(http, path + "/status", owner, "{\"active\":false}", null)))
          .isEqualTo(inactive);
      assertThat(itemRevision(id)).isEqualTo(revision);

      assertThat(successfulBody(patch(http, path + "/status", owner, "{\"active\":true}", null)))
          .isEqualTo(updated);
      assertThat(get(http, path, outsiderReader, tenant).statusCode()).isEqualTo(404);
      assertThat(patch(http, path, outsider, "{\"price\":1}", tenant).statusCode()).isEqualTo(404);
      assertThat(patch(http, path + "/status", outsider, "{\"active\":false}", tenant).statusCode())
          .isEqualTo(404);
      assertThat(items.find(tenant, id))
          .hasValueSatisfying(saved -> {
            assertThat(saved.id()).isEqualTo(id);
            assertThat(saved.companyId()).isEqualTo(tenant);
            assertThat(saved.description()).isEqualTo("Silla gris");
            assertThat(saved.price()).isEqualTo(new BigDecimal("3500.00"));
            assertThat(saved.active()).isTrue();
          });
    }
  }

  @Test
  @Tag("regression")
  void paginatesLiteralSearchResultsWithStableTenantScopedCounts() throws Exception {
    var tenant = UUID.randomUUID();
    var reader = membershipToken(tenant, MembershipRole.VIEWER, false, Scope.READ);
    var first = UUID.fromString("00000000-0000-0000-0000-000000000001");
    var second = UUID.fromString("00000000-0000-0000-0000-000000000002");
    seedItem(
        new CatalogItem(
            second, tenant, "Silla %_!", CatalogItemType.PRODUCT, BigDecimal.ONE, true));
    seedItem(
        new CatalogItem(first, tenant, "Silla %_!", CatalogItemType.PRODUCT, BigDecimal.ONE, true));
    seedItem(CatalogItem.register(tenant, "Silla ABC", CatalogItemType.PRODUCT, BigDecimal.ONE));
    seedItem(CatalogItem.register(tenant, "Silla %_!", CatalogItemType.SERVICE, BigDecimal.ONE));
    seedItem(
        CatalogItem.register(
            UUID.randomUUID(), "Silla %_!", CatalogItemType.PRODUCT, BigDecimal.ONE));
    seedItem(
        CatalogItem.register(tenant, "Silla %_!", CatalogItemType.PRODUCT, BigDecimal.ONE)
            .withActive(false));

    try (var http = HttpClient.newHttpClient()) {
      var path = CATALOG_ITEMS + "?type=PRODUCT&search=SILLA%20%25_%21&active=true&size=1";
      var page = successfulBody(get(http, path, reader));
      assertThat(page.path("totalElements").asLong()).isEqualTo(2);
      assertThat(page.path("totalPages").asLong()).isEqualTo(2);
      assertThat(page.path("content").get(0).path("id").asString()).isEqualTo(first.toString());
      page = successfulBody(get(http, path + "&page=1", reader));
      assertThat(page.path("content").get(0).path("id").asString()).isEqualTo(second.toString());
      page = successfulBody(get(http, path + "&page=2147483647", reader));
      assertThat(page.path("content").size()).isZero();
      assertThat(page.path("totalElements").asLong()).isEqualTo(2);
      page = successfulBody(get(http, CATALOG_ITEMS + "?search=missing", reader));
      assertThat(page.path("totalPages").asLong()).isZero();
      assertThat(page.path("content").size()).isZero();
      for (var query : List.of("?type=OTHER", "?active=TRUE", "?page=-1", "?size=101", "?size=0")) {
        assertThat(get(http, CATALOG_ITEMS + query, reader).statusCode()).isEqualTo(400);
      }
      assertThat(get(http, CATALOG_ITEMS + "/1-1-1-1-1", reader).statusCode()).isEqualTo(400);
    }
  }

  @Test
  @Tag("regression")
  void protectsBothDetailContractsWithCurrentTenantMembership() throws Exception {
    var companyA = UUID.randomUUID();
    var companyB = UUID.randomUUID();
    var readerA = membershipToken(companyA, MembershipRole.OPERATOR, false, Scope.READ);
    var readerB = membershipToken(companyB, MembershipRole.VIEWER, false, Scope.READ);
    var clientA = Client.register(companyA, new ClientDetails("Cliente A", null, null, null, null));
    var clientB = Client.register(companyB, new ClientDetails("Cliente B", null, null, null, null));
    seedClient(clientA);
    seedClient(clientB);
    var itemA = CatalogItem.register(companyA, "Silla A", CatalogItemType.PRODUCT, BigDecimal.ONE);
    var itemB = CatalogItem.register(companyB, "Silla B", CatalogItemType.SERVICE, BigDecimal.TEN);
    seedItem(itemA);
    seedItem(itemB);
    var pathsA = List.of("/api/v1/clients/" + clientA.id(), CATALOG_ITEMS + "/" + itemA.id());
    var pathsB = List.of("/api/v1/clients/" + clientB.id(), CATALOG_ITEMS + "/" + itemB.id());

    try (var http = HttpClient.newHttpClient()) {
      for (String path : pathsA) {
        assertThat(get(http, path, readerA, companyB).statusCode()).isEqualTo(200);
        assertThat(get(http, path, readerB, companyA).statusCode()).isEqualTo(404);
        assertThat(get(http, path, null, companyA).statusCode()).isEqualTo(401);
      }
      for (String path : pathsA) {
        var bound = get(http, path + "?expectedCompanyId=" + companyA, readerA, companyB);
        assertThat(bound.statusCode()).isEqualTo(200);
        assertThat(bound.headers().firstValue("X-Catalog-Company-Id"))
            .contains(companyA.toString());
        var mismatch = get(http, path + "?expectedCompanyId=" + companyB, readerA);
        assertThat(mismatch.statusCode()).isEqualTo(403);
        assertThat(mismatch.headers().firstValue("X-Catalog-Company-Id")).isEmpty();
        assertThat(get(http, path + "?expectedCompanyId=" + companyA, readerB).statusCode())
            .isEqualTo(403);
        assertThat(get(http, path + "?expectedCompanyId=" + companyB, readerB).statusCode())
            .isEqualTo(404);
      }
      for (String path : pathsB) {
        assertThat(get(http, path, readerB, companyA).statusCode()).isEqualTo(200);
        assertThat(get(http, path, readerA, companyB).statusCode()).isEqualTo(404);
      }
      stubMembership(readerA, companyB, MembershipRole.OPERATOR, false, Scope.READ);
      for (String path : pathsB) {
        assertThat(get(http, path + "?expectedCompanyId=" + companyA, readerA).statusCode())
            .isEqualTo(403);
        var bound = get(http, path + "?expectedCompanyId=" + companyB, readerA);
        assertThat(bound.statusCode()).isEqualTo(200);
        assertThat(bound.headers().firstValue("X-Catalog-Company-Id"))
            .contains(companyB.toString());
      }
      stubFailure(readerA, 403);
      for (String path : pathsA) {
        assertThat(get(http, path, readerA, companyA).statusCode()).isEqualTo(403);
      }
    }
  }

  @Test
  @Tag("regression")
  void checksCatalogItemPermissionsOnEveryOperation() throws Exception {
    var tenant = UUID.randomUUID();
    var admin = membershipToken(tenant, MembershipRole.ADMIN, false);
    var reader = membershipToken(tenant, MembershipRole.VIEWER, false, Scope.READ);
    var viewer = membershipToken(tenant, MembershipRole.VIEWER, false);
    var operator = membershipToken(tenant, MembershipRole.OPERATOR, false);
    var writer = membershipToken(tenant, MembershipRole.OPERATOR, true);
    var advisor = session("ADVISOR");
    stubFailure(advisor, 403);
    var advisorWithRead = session("ADVISOR");
    stubFailure(advisorWithRead, 403);
    var advisorId = SignedJWT.parse(advisorWithRead).getJWTClaimsSet().getSubject();
    COMPANY.stubFor(
        WireMock.get(urlEqualTo(PATH))
            .withHeader(USER_ASSERTION, WireMock.absent())
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {"userId":"%s","companyId":"%s","module":"CATALOG","scope":"READ","mode":"DELEGATED"}
                        """
                            .formatted(advisorId, tenant))));
    var timeout = session("REGULAR");
    COMPANY.stubFor(
        WireMock.get(urlEqualTo(PATH))
            .withHeader(USER_ASSERTION, equalTo(timeout))
            .willReturn(aResponse().withFixedDelay(3000)));
    var item = CatalogItem.register(tenant, "Silla", CatalogItemType.PRODUCT, BigDecimal.ONE);
    seedItem(item);
    var path = CATALOG_ITEMS + "/" + item.id();

    try (var http = HttpClient.newHttpClient()) {
      assertThat(get(http, CATALOG_ITEMS, null).statusCode()).isEqualTo(401);
      assertThat(get(http, path, "invalid").statusCode()).isEqualTo(401);
      assertThat(patch(http, path, null, "{\"price\":2}", null).statusCode()).isEqualTo(401);
      assertThat(get(http, path, reader).statusCode()).isEqualTo(200);
      assertThat(get(http, path, advisor, tenant).statusCode()).isEqualTo(403);
      for (var denied : List.of(viewer, operator, advisor, advisorWithRead)) {
        assertThat(patch(http, path, denied, "{\"price\":2}", tenant).statusCode()).isEqualTo(403);
        assertThat(patch(http, path + "/status", denied, "{\"active\":false}", tenant).statusCode())
            .isEqualTo(403);
      }
      COMPANY.verify(
          0, getRequestedFor(urlEqualTo(PATH)).withHeader(USER_ASSERTION, WireMock.absent()));
      var updated = successfulBody(patch(http, path, admin, "{\"description\":\"Gris\"}", null));
      assertThat(updated.path("description").asString()).isEqualTo("Gris");
      var cleared = successfulBody(patch(http, path, writer, "{\"description\":null}", null));
      assertThat(cleared.path("description").isNull()).isTrue();
      stubFailure(writer, 403);
      assertThat(patch(http, path + "/status", writer, "{\"active\":false}", null).statusCode())
          .isEqualTo(403);
      assertThat(patch(http, path, timeout, "{\"price\":2}", null).statusCode()).isEqualTo(503);
      for (var invalid :
          List.of("null", "{}", "{\"price\":10.999}", "{\"price\":-1}", "{\"active\":false}")) {
        assertThat(patch(http, path, admin, invalid, null).statusCode()).isEqualTo(400);
      }
      assertThat(patch(http, path + "/status", admin, "{\"active\":\"false\"}", null).statusCode())
          .isEqualTo(400);
      assertThat(successfulBody(get(http, path, reader))).isEqualTo(cleared);
      assertThat(itemRevision(item.id())).isEqualTo(2);
    }
  }

  @Test
  @Tag("regression")
  void detectsConcurrentCatalogItemChangesInPostgres() throws Exception {
    var item =
        CatalogItem.register(UUID.randomUUID(), "Silla", CatalogItemType.PRODUCT, BigDecimal.ONE);
    seedItem(item);
    var read = new CountDownLatch(1);
    var committed = new CountDownLatch(1);
    try (var executor = Executors.newSingleThreadExecutor()) {
      var stale =
          executor.submit(() ->
              items.update(
                  item.companyId(),
                  item.id(),
                  current -> {
                    read.countDown();
                    try {
                      if (!committed.await(10, TimeUnit.SECONDS)) {
                        throw new AssertionError("Concurrent write did not finish");
                      }
                    } catch (InterruptedException interrupted) {
                      Thread.currentThread().interrupt();
                      throw new AssertionError(interrupted);
                    }
                    return current.withActive(false);
                  }));
      try {
        assertThat(read.await(10, TimeUnit.SECONDS)).isTrue();
        items.update(
            item.companyId(),
            item.id(),
            current ->
                current.apply(
                    new CatalogItemPatch(
                        Optional.empty(), Optional.empty(), Optional.of(new BigDecimal("3.50")))));
      } finally {
        committed.countDown();
      }
      assertThatThrownBy(() -> stale.get(10, TimeUnit.SECONDS))
          .hasCauseInstanceOf(CatalogItemAccessException.class)
          .hasRootCauseMessage("CATALOG_ITEM_UPDATE_CONFLICT");
    }
    assertThat(items.find(item.companyId(), item.id()))
        .hasValueSatisfying(saved -> {
          assertThat(saved.active()).isTrue();
          assertThat(saved.price()).isEqualTo(new BigDecimal("3.50"));
        });
    assertThat(itemRevision(item.id())).isEqualTo(1);
  }

  @Test
  void catalogItemManagementRequiresAuthenticationThroughTheSpringProxy() {
    var id = UUID.randomUUID();
    assertThatThrownBy(() -> listCatalogItems.execute(null, null, null, 0, 20))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> getCatalogItem.execute(id)).isInstanceOf(AccessDeniedException.class);
    var patch = new CatalogItemPatch(Optional.of("Silla"), Optional.empty(), Optional.empty());
    assertThatThrownBy(() -> updateCatalogItem.execute(id, patch))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> changeCatalogItemStatus.execute(id, false))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @Tag("smoke")
  void managesCommercialItemsThroughHttp() throws Exception {
    var owner = scopedCatalogOwner(UUID.randomUUID());
    var outsider = scopedCatalogOwner(UUID.randomUUID());
    try (var http = HttpClient.newHttpClient()) {
      var categoryRef = successfulBody(get(http, CATALOG_ITEMS + "/categories", owner)).get(0);
      var category = categoryRef.path("id").asString();
      assertThat(successfulBody(get(http, CATALOG_ITEMS + "/units", owner)).size()).isEqualTo(7);
      var tax = successfulBody(get(http, CATALOG_ITEMS + "/tax-classifications", owner));
      assertThat(tax)
          .anySatisfy(entry -> assertThat(entry.path("code").asString()).isEqualTo("ITBIS_18"));

      var body = commercialItem(category);
      var created = postItem(http, owner, body, null);
      var id = createdId(created);
      assertThat(
              postItem(
                      http,
                      owner,
                      body.replace("PRODUCT", "SERVICE")
                          .replace("mon-01", "SVC-01")
                          .replace("000123", "000124"),
                      null)
                  .statusCode())
          .isEqualTo(201);
      var byCategory = CATALOG_ITEMS + "?search=" + categoryRef.path("code").asString() + "&size=1";
      var categoryPage = successfulBody(get(http, byCategory, owner));
      assertThat(categoryPage.path("totalElements").asInt()).isEqualTo(2);
      assertThat(categoryPage.path("content").size()).isEqualTo(1);
      assertThat(successfulBody(get(http, byCategory, outsider)).path("totalElements").asInt())
          .isZero();
      var path = CATALOG_ITEMS + "/" + id;
      var filtered =
          successfulBody(
              get(
                  http,
                  CATALOG_ITEMS + "?search=mon-01&availableForPurchase=true&categoryId=" + category,
                  owner));
      assertThat(filtered.path("totalElements").asInt()).isEqualTo(1);
      assertThat(filtered.path("content").get(0).path("id").asString()).isEqualTo(id);
      var detail = successfulBody(get(http, path, owner));
      assertThat(detail.path("barcode").asString()).isEqualTo("000123");
      assertThat(detail.path("estimatedCost").asString()).isEqualTo("1000.0001");
      assertThat(detail.path("taxRate").asString()).isEqualTo("0.180000");
      assertThat(detail.path("finalPrice").asString()).isEqualTo("1770.00");
      assertThat(detail.path("grossMargin").asString()).isEqualTo("33.33");

      var update = "{\"price\":\"1600.0000\",\"estimatedCost\":\"1100.0001\"}";
      assertThat(patch(http, path, owner, update, null).statusCode()).isEqualTo(200);
      assertThat(patch(http, path, owner, update, null).statusCode()).isEqualTo(200);
      var history = successfulBody(get(http, path + "/history", owner));
      assertThat(history.path("totalElements").asInt()).isEqualTo(2);
      assertThat(history.path("content").get(0).path("actorId").isNull()).isFalse();
      assertThat(history.path("content").get(0).path("operation").asString()).isEqualTo("UPDATE");

      var supplier = createCommercialSupplier(http, owner, "Proveedor comercial", null);
      var links = SUPPLIERS + "/" + supplier + "/items";
      assertThat(putSupplier(http, links, owner, offer(id, true, false)).statusCode())
          .isEqualTo(204);
      assertThat(successfulBody(get(http, path, owner)).path("primarySupplierId").asString())
          .isEqualTo(supplier);
      var unavailable =
          successfulBody(
              patch(http, path, owner, "{\"availableForPurchase\":false,\"barcode\":null}", null));
      assertThat(unavailable.path("eligibleForPurchase").asBoolean()).isFalse();
      assertThat(unavailable.path("primarySupplierId").isNull()).isTrue();
      assertThat(unavailable.path("barcode").isNull()).isTrue();
      assertThat(
              successfulBody(get(http, path + "/suppliers", owner)).path("totalElements").asInt())
          .isEqualTo(1);
      assertThat(putSupplier(http, links, owner, offer(id, true, false)).statusCode())
          .isEqualTo(409);
      assertThat(
              successfulBody(patch(http, path + "/status", owner, "{\"active\":false}", null))
                  .path("eligibleForSale")
                  .asBoolean())
          .isFalse();
      assertThat(successfulBody(get(http, CATALOG_ITEMS + "/summary", owner)).path("total").asInt())
          .isEqualTo(2);
      assertThat(
              successfulBody(get(http, CATALOG_ITEMS + "/summary", outsider)).path("total").asInt())
          .isZero();
      assertThat(get(http, path + "/history", outsider).statusCode()).isEqualTo(404);
      assertThat(postItem(http, outsider, body, null).statusCode()).isEqualTo(404);
    }
  }

  @Test
  @Tag("regression")
  void concurrentCommercialCodesAndCategoryInitializationRemainUnique() throws Exception {
    var tenant = UUID.randomUUID();
    var owner = scopedCatalogOwner(tenant);
    var outsider = scopedCatalogOwner(UUID.randomUUID());
    try (var http = HttpClient.newHttpClient()) {
      var path = CATALOG_ITEMS + "/categories";
      assertThat(
              race(
                  () -> get(http, path, owner).statusCode(),
                  () -> get(http, path, owner).statusCode()))
          .containsExactly(200, 200);
      var categories = successfulBody(get(http, path, owner));
      assertThat(categories.size()).isEqualTo(5);
      var id = categories.get(0).path("id").asString();
      var body = commercialItem(id);
      assertThat(
              race(
                  () -> postItem(http, owner, body, null).statusCode(),
                  () -> postItem(http, owner, body.replace("000123", "000124"), null).statusCode()))
          .containsExactlyInAnyOrder(201, 409);
      var barcodeOnly = body.replace("\"sku\":\" mon-01 \",", "");
      assertThat(
              race(
                  () ->
                      postItem(http, owner, barcodeOnly.replace("000123", "001555"), null)
                          .statusCode(),
                  () ->
                      postItem(http, owner, barcodeOnly.replace("000123", "001555"), null)
                          .statusCode()))
          .containsExactlyInAnyOrder(201, 409);
      var otherCategory = successfulBody(get(http, path, outsider)).get(0).path("id").asString();
      assertThat(postItem(http, outsider, commercialItem(otherCategory), null).statusCode())
          .isEqualTo(201);
    }
  }

  @Test
  @Tag("regression")
  void preservesExactCommercialAmountsInPostgres() {
    var profile =
        new com.sanigest.catalog.domain.model.CatalogItemProfile(
            " exact ",
            "0001",
            null,
            "UNIT",
            new BigDecimal("999999999999999.9999"),
            "ITBIS_16",
            true,
            false);
    var item =
        new CatalogItem(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Precisión",
            CatalogItemType.SERVICE,
            new BigDecimal("11.2345"),
            true,
            null,
            profile);
    seedItem(item);
    assertThat(items.find(item.companyId(), item.id())).contains(item);
  }

  @Test
  @Tag("regression")
  void resolvesRateVersionsWithExclusiveEndDatesAndRejectsOverlap() {
    var jdbc = new JdbcTemplate(dataSource);
    var code = "VERSION_TEST";
    jdbc.update("INSERT INTO catalog.tax_classifications VALUES (?, 'Version test')", code);
    jdbc.update(
        """
        INSERT INTO catalog.tax_rate_versions VALUES
          (?, DATE '2026-01-01', DATE '2026-05-01', 0.180000),
          (?, DATE '2026-05-01', DATE '2026-08-01', 0.160000),
          (?, DATE '2027-01-01', NULL, 0.140000)
        """,
        code,
        code,
        code);
    assertThat(rateOn(code, "2025-12-31")).isNull();
    assertThat(rateOn(code, "2026-01-01")).isEqualTo(new BigDecimal("0.180000"));
    assertThat(rateOn(code, "2026-04-30")).isEqualTo(new BigDecimal("0.180000"));
    assertThat(rateOn(code, "2026-05-01")).isEqualTo(new BigDecimal("0.160000"));
    assertThat(rateOn(code, "2026-08-01")).isNull();
    assertThat(rateOn(code, "2027-01-01")).isEqualTo(new BigDecimal("0.140000"));
    assertThatThrownBy(() ->
            jdbc.update(
                """
                INSERT INTO catalog.tax_rate_versions VALUES (?, DATE '2026-02-01', NULL, 0.120000)
                """,
                code))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() ->
            jdbc.update(
                """
                UPDATE catalog.tax_rate_versions SET rate = 0.130000
                WHERE classification_code = ? AND effective_from = DATE '2026-01-01'
                """,
                code))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private BigDecimal rateOn(String code, String date) {
    return itemCommercial.taxes(java.time.LocalDate.parse(date)).stream()
        .filter(tax -> tax.code().equals(code))
        .findFirst()
        .orElseThrow()
        .rate();
  }

  @Test
  @Tag("regression")
  void rollsBackTheCommercialChangeWhenItsHistoryInsertFails() {
    var item =
        CatalogItem.register(
            UUID.randomUUID(), "Rollback", CatalogItemType.PRODUCT, BigDecimal.TEN);
    seedItem(item);
    var jdbc = new JdbcTemplate(dataSource);
    var transaction = new TransactionTemplate(transactions);
    assertThatThrownBy(() ->
            transaction.executeWithoutResult(status -> {
              jdbc.execute(
                  """
                  ALTER TABLE catalog.catalog_item_history ADD CONSTRAINT test_audit_rejection
                  CHECK (catalog_item_id <> '%s' OR operation <> 'UPDATE')
                  """
                      .formatted(item.id()));
              items.update(
                  item.companyId(),
                  item.id(),
                  current ->
                      current.apply(
                          new CatalogItemPatch(
                              Optional.empty(), Optional.empty(), Optional.of(BigDecimal.ONE))));
            }))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(items.find(item.companyId(), item.id())).contains(completeItem(item));
    assertThat(itemRevision(item.id())).isZero();
    assertThat(itemCommercial.history(item.companyId(), item.id(), 0, 20).totalElements())
        .isEqualTo(1);
  }

  @Test
  @Tag("regression")
  void purchasingChangeAndPrimaryDesignationStayConsistentWhenConcurrent() throws Exception {
    var owner = scopedCatalogOwner(UUID.randomUUID());
    try (var http = HttpClient.newHttpClient()) {
      var category =
          successfulBody(get(http, CATALOG_ITEMS + "/categories", owner))
              .get(0)
              .path("id")
              .asString();
      var id = createdId(postItem(http, owner, commercialItem(category), null));
      var supplier = createCommercialSupplier(http, owner, "Concurrent supplier", null);
      var itemPath = CATALOG_ITEMS + "/" + id;
      var supplierPath = SUPPLIERS + "/" + supplier + "/items";
      var results =
          race(
              () -> putSupplier(http, supplierPath, owner, offer(id, true, false)).statusCode(),
              () ->
                  patch(http, itemPath, owner, "{\"availableForPurchase\":false}", null)
                      .statusCode());
      assertThat(results.getFirst()).isIn(204, 409);
      assertThat(results.getLast()).isEqualTo(200);
      var item = successfulBody(get(http, itemPath, owner));
      assertThat(item.path("eligibleForPurchase").asBoolean()).isFalse();
      assertThat(item.path("primarySupplierId").isNull()).isTrue();
    }
  }

  private static String commercialItem(String categoryId) {
    return """
    {"name":"Monitor","type":"PRODUCT","sku":" mon-01 ","barcode":"000123",
     "categoryId":"%s","unitCode":"UNIT","estimatedCost":"1000.0001","price":"1500.0000",
     "taxClassificationCode":"ITBIS_18","availableForPurchase":true}
    """
        .formatted(categoryId);
  }

  private long itemRevision(UUID id) {
    return new JdbcTemplate(dataSource)
        .queryForObject("SELECT revision FROM catalog.catalog_items WHERE id = ?", Long.class, id);
  }

  private HttpResponse<String> postItem(
      HttpClient http, String token, String json, UUID companyHeader)
      throws IOException, InterruptedException {
    var request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + CATALOG_ITEMS))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json));
    if (token != null) {
      request.header("Authorization", "Bearer " + token);
    }
    if (token != null) {
      request.header(
          "X-Company-Id",
          SELECTORS
              .getOrDefault(token, companyHeader == null ? new UUID(0, 0) : companyHeader)
              .toString());
    }
    return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private long itemCount(UUID company) {
    return new JdbcTemplate(dataSource)
        .queryForObject(
            "SELECT count(*) FROM catalog.catalog_items WHERE company_id = ?", Long.class, company);
  }

  private void assertPersistedItem(
      HttpResponse<String> response, UUID company, String name, String type, String price) {
    assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    assertThat(response.body()).contains("\"price\":" + price);
    var json = JsonMapper.shared().readTree(response.body());
    var id = UUID.fromString(json.path("id").asString());
    assertThat(json.path("companyId").asString()).isEqualTo(company.toString());
    assertThat(json.path("name").asString()).isEqualTo(name);
    assertThat(json.path("type").asString()).isEqualTo(type);
    assertThat(json.path("currency").asString()).isEqualTo("DOP");
    assertThat(json.path("active").asBoolean()).isTrue();
    assertThat(json.has("stock")).isFalse();
    assertThat(
            new JdbcTemplate(dataSource)
                .queryForMap(
                    """
                    SELECT company_id, name, type, price, currency, active FROM catalog.catalog_items WHERE id = ?
                    """,
                    id))
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "company_id",
                company,
                "name",
                name,
                "type",
                type,
                "price",
                new BigDecimal(price).setScale(4),
                "currency",
                "DOP",
                "active",
                true));
  }

  private static void assertConnectionRejected(
      String database, String user, String password, String state) {
    String url =
        "jdbc:postgresql://%s:%d/%s"
            .formatted(POSTGRES.getHost(), POSTGRES.getMappedPort(5432), database);
    assertThatThrownBy(
            () -> {
              try (var unexpected = DriverManager.getConnection(url, user, password)) {
                assertThat(unexpected.isClosed()).isFalse();
              }
            })
        .isInstanceOfSatisfying(
            SQLException.class, failure -> assertThat(failure.getSQLState()).isEqualTo(state));
  }

  private HttpResponse<String> get(HttpClient http, String path, String token)
      throws IOException, InterruptedException {
    return get(http, path, token, null);
  }

  private HttpResponse<String> get(
      HttpClient http, String path, String token, UUID untrustedCompanyId)
      throws IOException, InterruptedException {
    var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
    if (token != null) {
      request.header(
          "X-Company-Id",
          SELECTORS
              .getOrDefault(token, untrustedCompanyId == null ? new UUID(0, 0) : untrustedCompanyId)
              .toString());
    }
    if (token != null) {
      request.header("Authorization", "Bearer " + token);
    }
    return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private static String token(KeyPair key, String issuer, String audience, Instant expiration)
      throws JOSEException {
    return token(key, issuer, audience, expiration, UUID.randomUUID(), "REGULAR");
  }

  private static String token(
      KeyPair key,
      String issuer,
      String audience,
      Instant expiration,
      UUID userId,
      String accountType)
      throws JOSEException {
    var claims =
        new JWTClaimsSet.Builder()
            .issuer(issuer)
            .audience(audience)
            .subject(userId.toString())
            .claim("accountType", accountType)
            .claim("role", "OWNER")
            .claim("permissions", List.of("CATALOG_WRITE"))
            .issueTime(Date.from(Instant.now().minusSeconds(300)))
            .expirationTime(Date.from(expiration))
            .build();
    var jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).build(), claims);
    jwt.sign(new RSASSASigner(key.getPrivate()));
    return jwt.serialize();
  }

  private static KeyPair rsaKey() {
    try {
      var generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("Cannot generate a test RSA key", exception);
    }
  }

  private static Path writeKey(String type, byte[] key) {
    try {
      var file = Files.createTempFile("catalog-jwt-", ".pem");
      var encoded = Base64.getMimeEncoder().encodeToString(key);
      return Files.writeString(
          file, "-----BEGIN " + type + "-----\n" + encoded + "\n-----END " + type + "-----\n");
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  @Test
  @Tag("regression")
  void filtersStockAcrossCommercialBatchesWithExactPagination() throws Exception {
    var tenant = UUID.randomUUID();
    var warehouse = UUID.randomUUID();
    var reader = membershipToken(tenant, MembershipRole.OWNER, false, Scope.READ);
    var foreign = membershipToken(UUID.randomUUID(), MembershipRole.OWNER, false, Scope.READ);
    var jdbc = new JdbcTemplate(dataSource);
    jdbc.update(
        """
        INSERT INTO catalog.catalog_items
          (id, company_id, name, type, price, unit_code, tax_classification_code, available_for_purchase)
        SELECT gen_random_uuid(), ?, 'Stock ' || lpad(n::text, 3, '0') ||
          CASE WHEN n = 100 THEN ' ' ELSE '' END, 'PRODUCT', 1, 'UNIT', 'EXEMPT', false
        FROM generate_series(1, 105) n
        """,
        tenant);
    var out =
        jdbc.queryForList(
            """
            SELECT id FROM catalog.catalog_items
            WHERE company_id = ? AND name IN ('Stock 101', 'Stock 103') ORDER BY name
            """,
            UUID.class,
            tenant);
    var stock = "/api/v1/inventory/stock";
    COMPANY.stubFor(
        WireMock.get(WireMock.urlPathEqualTo(stock))
            .withHeader("X-Company-Id", equalTo(tenant.toString()))
            .withHeader("Authorization", equalTo("Bearer " + reader))
            .willReturn(WireMock.okJson("{\"content\":[],\"totalElements\":0}")));
    COMPANY.stubFor(
        WireMock.get(WireMock.urlPathEqualTo(stock))
            .withHeader("X-Company-Id", equalTo(tenant.toString()))
            .withHeader("Authorization", equalTo("Bearer " + reader))
            .withQueryParam("warehouseId", equalTo(warehouse.toString()))
            .withQueryParam("catalogItemIds", WireMock.containing(out.getFirst().toString()))
            .willReturn(WireMock.okJson(stockPositions(warehouse, out))));
    COMPANY.stubFor(
        WireMock.get(urlEqualTo("/api/v1/warehouses/" + warehouse))
            .withHeader("Authorization", equalTo("Bearer " + foreign))
            .willReturn(aResponse().withStatus(404)));
    var path =
        CATALOG_ITEMS
            + "?search=Stock&type=PRODUCT&active=true"
            + "&availability=OUT&warehouseId="
            + warehouse
            + "&size=1";
    try (var http = HttpClient.newHttpClient()) {
      var first = successfulBody(get(http, path, reader));
      var second = successfulBody(get(http, path + "&page=1", reader));
      assertFilteredStockPage(first, 0, out.getFirst());
      assertFilteredStockPage(second, 1, out.getLast());
      assertThat(get(http, path, foreign).statusCode()).isEqualTo(404);
      assertThat(get(http, CATALOG_ITEMS + "?availability=OUT", reader).statusCode())
          .isEqualTo(400);
      var notTracked =
          successfulBody(
              get(http, path.replace("availability=OUT", "availability=NOT_TRACKED"), reader));
      assertThat(notTracked.path("totalElements").asInt()).isEqualTo(103);
      assertThat(notTracked.path("totalPages").asInt()).isEqualTo(103);
      COMPANY.verify(6, getRequestedFor(WireMock.urlPathEqualTo(stock)));
      COMPANY.stubFor(
          WireMock.get(WireMock.urlPathEqualTo(stock))
              .atPriority(1)
              .willReturn(aResponse().withStatus(503)));
      var failure = get(http, path, reader);
      assertThat(failure.statusCode()).isEqualTo(503);
      assertThat(JsonMapper.shared().readTree(failure.body()).path("errorCode").asString())
          .isEqualTo("INVENTORY_UNAVAILABLE");
    }
  }

  private static void assertFilteredStockPage(JsonNode body, int page, UUID item) {
    assertThat(body.path("page").asInt()).isEqualTo(page);
    assertThat(body.path("size").asInt()).isEqualTo(1);
    assertThat(body.path("totalElements").asLong()).isEqualTo(2);
    assertThat(body.path("totalPages").asInt()).isEqualTo(2);
    assertThat(body.path("content").isArray()).isTrue();
    assertThat(body.path("content"))
        .extracting(row -> row.path("id").asString())
        .containsExactly(item.toString());
  }

  private static String stockPositions(UUID warehouse, List<UUID> ids) {
    var content =
        ids.stream()
            .map(id -> Map.of("catalogItemId", id, "warehouseId", warehouse, "availability", "OUT"))
            .toList();
    return JsonMapper.shared()
        .writeValueAsString(Map.of("content", content, "totalElements", ids.size()));
  }

  private void seedClient(Client client) {
    clientCategories.initialize(client.companyId());
    var profile =
        new ClientProfile(
            ClientType.LEGAL,
            null,
            "US",
            null,
            null,
            null,
            ClientPaymentTerms.CASH,
            clientCategories.defaultId(client.companyId()),
            null,
            null);
    clients.insert(
        new Client(
            client.id(),
            client.companyId(),
            client.details().withProfile(profile),
            client.status()));
  }

  private void seedSupplier(Supplier supplier) {
    suppliers.insert(completeSupplier(supplier));
  }

  private static Supplier completeSupplier(Supplier supplier) {
    var d = supplier.details();
    var profile = new SupplierProfile(null, null, SupplierPaymentTerms.CASH, null, null, null);
    return new Supplier(
        supplier.id(),
        supplier.companyId(),
        new SupplierDetails(d.name(), d.taxId(), d.contact(), d.phone(), d.email(), profile),
        supplier.status());
  }

  private void seedItem(CatalogItem item) {
    items.insert(completeItem(item));
  }

  private static CatalogItem completeItem(CatalogItem item) {
    if (!item.profile().equals(CatalogItemProfile.LEGACY)) {
      return item;
    }
    var profile = new CatalogItemProfile(null, null, null, "UNIT", null, "EXEMPT", true, false);
    return new CatalogItem(
        item.id(),
        item.companyId(),
        item.name(),
        item.type(),
        item.price(),
        item.active(),
        item.description(),
        profile);
  }

  private static void assertIncompleteInsertsRejected(JdbcTemplate jdbc, UUID companyId) {
    for (var sql :
        List.of(
            "INSERT INTO catalog.clients (id, company_id, name, status) VALUES (?, ?, 'New', 'ACTIVE')",
            "INSERT INTO catalog.suppliers (id, company_id, name, contact) VALUES (?, ?, 'New', 'Contact')",
            """
            INSERT INTO catalog.catalog_items (id, company_id, name, type, price)
            VALUES (?, ?, 'New', 'PRODUCT', 1)
            """)) {
      assertThatThrownBy(() -> jdbc.update(sql, UUID.randomUUID(), companyId))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("requires");
    }
  }
}
