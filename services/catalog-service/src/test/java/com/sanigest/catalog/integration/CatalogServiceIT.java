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
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sanigest.catalog.CatalogServiceApplication;
import com.sanigest.catalog.application.exception.CatalogItemAccessException;
import com.sanigest.catalog.application.exception.ClientAccessException;
import com.sanigest.catalog.application.exception.ClientCreationException;
import com.sanigest.catalog.application.exception.SupplierAccessException;
import com.sanigest.catalog.application.port.out.persistence.CatalogItemRepository;
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
import com.sanigest.catalog.domain.enums.ClientStatus;
import com.sanigest.catalog.domain.enums.SupplierStatus;
import com.sanigest.catalog.domain.model.CatalogItem;
import com.sanigest.catalog.domain.model.CatalogItemPatch;
import com.sanigest.catalog.domain.model.Client;
import com.sanigest.catalog.domain.model.ClientDetails;
import com.sanigest.catalog.domain.model.ClientPatch;
import com.sanigest.catalog.domain.model.ClientPatch.Field;
import com.sanigest.catalog.domain.model.Supplier;
import com.sanigest.catalog.domain.model.SupplierDetails;
import com.sanigest.catalog.domain.model.SupplierPatch;
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
      {"name":"Consultoría","type":"SERVICE","price":15000.00}
      """;
  private static final String PRODUCT_ITEM =
      """
      {"name":"Silla","type":"PRODUCT","price":3200.00}
      """;

  @Autowired CreateCatalogItem createCatalogItem;
  @Autowired CatalogItemRepository items;
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

  @DynamicPropertySource
  static void configure(DynamicPropertyRegistry registry) {
    if (!COMPANY.isRunning()) {
      COMPANY.start();
    }
    registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> true);
    registry.add("sanigest.company-access.enabled", () -> true);
    registry.add("sanigest.company-access.origin", COMPANY::baseUrl);
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
            "catalog.catalog_items");
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
            "V8__support_catalog_item_management.sql");
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
                   "address":"Santo Domingo","companyId":"%s","userId":"%s","status":"DISABLED","id":"%s"}
                  """
                      .formatted(taxId, otherCompanyId, UUID.randomUUID(), UUID.randomUUID()),
                  otherCompanyId),
              HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).isEqualTo(201);
      var mapper = JsonMapper.shared();
      var body = mapper.readTree(response.body());
      var id = UUID.fromString(body.path("id").asString());
      assertThat(body)
          .isEqualTo(
              mapper.readTree(
                  """
                  {"id":"%s","name":"Client","taxId":"%s","status":"ACTIVE"}
                  """
                      .formatted(id, taxId)));
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
      assertThat(postStatus(http, nullTaxIdToken, "{\"name\":\"First\"}")).isEqualTo(201);
      assertThat(postStatus(http, nullTaxIdToken, "{\"name\":\"Second\",\"taxId\":null}"))
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
        {"name":"Denied","taxId":"%s","companyId":"%s","userId":"%s"}
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
    try {
      var details = new ClientDetails("Client", taxId, null, null, null);
      assertThatThrownBy(() -> createClient.execute(details))
          .isInstanceOfSatisfying(
              ClientCreationException.class,
              failure ->
                  assertThat(failure.reason()).isEqualTo(ClientCreationException.Reason.FORBIDDEN));
    } finally {
      SecurityContextHolder.clearContext();
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
          .containsExactlyInAnyOrder("id", "name", "taxId", "status");
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
    assertThatThrownBy(() -> listClients.execute(null, null, 0, 20))
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
  void rejectsAStaleConcurrentUpdateWithoutLosingTheCommittedChange() throws Exception {
    var original =
        Client.register(
            UUID.randomUUID(), new ClientDetails("Concurrent client", null, null, null, null));
    clients.insert(original);
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
                current.updateDetails(new ClientPatch(Map.of(Field.EMAIL, "saved@example.org"))));
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
    var latest = Flyway.configure().dataSource(source).load();
    assertThat(latest.migrate().migrationsExecuted).isEqualTo(1);
    assertThat(
            jdbc.queryForMap(
                """
                SELECT company_id, description, price, revision FROM catalog.catalog_items WHERE id = ?
                """,
                itemId))
        .containsEntry("company_id", companyId)
        .containsEntry("description", null)
        .containsEntry("price", new BigDecimal("3200.50"))
        .containsEntry("revision", 0L);
    latest.validate();
    assertThat(
            jdbc.queryForMap(
                "SELECT company_id, tax_id, status, revision FROM catalog.suppliers WHERE id = ?",
                supplierId))
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("company_id", companyId, "tax_id", "123", "status", "ACTIVE", "revision", 0L));
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
  }

  @Test
  @Tag("regression")
  void readsThePersistedEditAndStatusFromAFreshApplicationContext() {
    var original =
        Client.register(
            UUID.randomUUID(), new ClientDetails("Persistent client", "321", null, null, null));
    clients.insert(original);
    var stored =
        clients.update(
            original.companyId(),
            original.id(),
            current ->
                current
                    .updateDetails(new ClientPatch(Map.of(Field.EMAIL, "persisted@example.org")))
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
    if (companyHeader != null) {
      request.header("X-Company-Id", companyHeader.toString());
    }
    return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private static JsonNode successfulBody(HttpResponse<String> response) {
    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    return JsonMapper.shared().readTree(response.body());
  }

  private String createForListing(HttpClient http, String token, String name, String taxId)
      throws Exception {
    var body = JsonMapper.shared().createObjectNode().put("name", name).put("taxId", taxId);
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
    return jwt;
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
    return "{\"name\":\"Client\",\"taxId\":\"%s\"}".formatted(taxId);
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
    if (untrustedCompanyId != null) {
      request.header("X-Company-Id", untrustedCompanyId.toString());
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
        {"name":" Office ","taxId":"  A-123  ","contact":" Laura ","phone":"+1 (809) 555-0118",
         "email":"sales@example.do","companyId":"%s","id":"%s","status":"INACTIVE"}
        """
            .formatted(otherCompany, forgedId);

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
          .containsEntry("tax_id", "A-123")
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
      assertThat(postSupplier(http, a, withTax.formatted(" TAX-1 "), null).statusCode())
          .isEqualTo(201);
      var duplicate = postSupplier(http, a, withTax.formatted("TAX-1"), null);
      assertThat(duplicate.statusCode()).isEqualTo(409);
      assertThat(JsonMapper.shared().readTree(duplicate.body()).path("errorCode").asString())
          .isEqualTo("DUPLICATE_TAX_ID");
      assertThat(duplicate.body()).doesNotContain("TAX-1", "uq_suppliers_company_tax_id");
      assertThat(postSupplier(http, b, withTax.formatted("TAX-1"), null).statusCode())
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
              suppliers.insert(supplier);
              assertThat(supplierCount(company)).isEqualTo(1);
              jdbc.update(
                  "UPDATE catalog.suppliers SET status = 'UNKNOWN' WHERE id = ?", supplier.id());
            }))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(supplierCount(company)).isZero();

    suppliers.insert(supplier);
    assertThatThrownBy(() -> suppliers.insert(supplier))
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
              {"name":"Office North","taxId":"ABC-101","contact":"Laura","phone":"8095550101"}
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
      var page = successfulBody(get(http, SUPPLIERS + "?search=bc-10&status=inactive", viewer));
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
    suppliers.insert(second);
    suppliers.insert(first);
    suppliers.insert(alpha);
    suppliers.insert(Supplier.register(UUID.randomUUID(), original));

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
          successfulBody(get(http, SUPPLIERS + "?search=Hidden%20Contact", reader)), 0, 20, 0, 0);
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
    suppliers.insert(supplier);
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
        Supplier.register(tenant, new SupplierDetails("First", "Reserved", "Laura", null, null));
    var second =
        Supplier.register(tenant, new SupplierDetails("Second", null, "Laura", null, null));
    suppliers.insert(first.changeStatus(SupplierStatus.INACTIVE));
    suppliers.insert(second);
    try (var http = HttpClient.newHttpClient()) {
      var duplicate =
          patch(http, SUPPLIERS + "/" + second.id(), writer, "{\"taxId\":\" Reserved \"}", null);
      assertThat(duplicate.statusCode()).isEqualTo(409);
      assertThat(JsonMapper.shared().readTree(duplicate.body()).path("errorCode").asString())
          .isEqualTo("DUPLICATE_TAX_ID");
      assertThat(suppliers.find(tenant, second.id())).contains(second);
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
    if (forgedCompany != null) {
      request.header("X-Company-Id", forgedCompany.toString());
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
    assertThatThrownBy(
            () -> createCatalogItem.execute("Chair", CatalogItemType.PRODUCT, BigDecimal.ONE))
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
            List.of("Chair", "PRODUCT", "1000000000000", "DOP"),
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
    items.insert(
        new CatalogItem(
            second, tenant, "Silla %_!", CatalogItemType.PRODUCT, BigDecimal.ONE, true));
    items.insert(
        new CatalogItem(first, tenant, "Silla %_!", CatalogItemType.PRODUCT, BigDecimal.ONE, true));
    items.insert(
        CatalogItem.register(tenant, "Silla ABC", CatalogItemType.PRODUCT, BigDecimal.ONE));
    items.insert(
        CatalogItem.register(tenant, "Silla %_!", CatalogItemType.SERVICE, BigDecimal.ONE));
    items.insert(
        CatalogItem.register(
            UUID.randomUUID(), "Silla %_!", CatalogItemType.PRODUCT, BigDecimal.ONE));
    items.insert(
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
    clients.insert(clientA);
    clients.insert(clientB);
    var itemA = CatalogItem.register(companyA, "Silla A", CatalogItemType.PRODUCT, BigDecimal.ONE);
    var itemB = CatalogItem.register(companyB, "Silla B", CatalogItemType.SERVICE, BigDecimal.TEN);
    items.insert(itemA);
    items.insert(itemB);
    var pathsA = List.of("/api/v1/clients/" + clientA.id(), CATALOG_ITEMS + "/" + itemA.id());
    var pathsB = List.of("/api/v1/clients/" + clientB.id(), CATALOG_ITEMS + "/" + itemB.id());

    try (var http = HttpClient.newHttpClient()) {
      for (String path : pathsA) {
        assertThat(get(http, path, readerA, companyB).statusCode()).isEqualTo(200);
        assertThat(get(http, path, readerB, companyA).statusCode()).isEqualTo(404);
        assertThat(get(http, path, null, companyA).statusCode()).isEqualTo(401);
      }
      for (String path : pathsB) {
        assertThat(get(http, path, readerB, companyA).statusCode()).isEqualTo(200);
        assertThat(get(http, path, readerA, companyB).statusCode()).isEqualTo(404);
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
    items.insert(item);
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
    items.insert(item);
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
    if (companyHeader != null) {
      request.header("X-Company-Id", companyHeader.toString());
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
                new BigDecimal(price),
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
    if (untrustedCompanyId != null) {
      request.header("X-Company-Id", untrustedCompanyId.toString());
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
}
