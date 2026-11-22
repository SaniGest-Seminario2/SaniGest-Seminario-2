package com.sanigest.company.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@Tag("regression")
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CompanyServiceApplicationIT {

  @LocalServerPort private int port;

  @Autowired private Flyway flyway;
  @Autowired private JdbcTemplate jdbcTemplate;

  private final HttpClient httpClient = HttpClient.newHttpClient();

  @Test
  void appliesInitialMigrationExactlyOnce() {
    Integer appliedMigrations =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM "flyway_schema_history"
            WHERE "version" = '1' AND "success" = TRUE
            """,
            Integer.class);
    Integer companyTable =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = 'PUBLIC' AND table_name = 'COMPANIES'
            """,
            Integer.class);

    assertThat(appliedMigrations).isEqualTo(1);
    assertThat(companyTable).isEqualTo(1);
    Integer membershipTable =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = 'PUBLIC' AND table_name = 'COMPANY_MEMBERSHIPS'",
            Integer.class);
    Integer permissionTable =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = 'PUBLIC' AND table_name = 'COMPANY_PERMISSIONS'",
            Integer.class);
    assertThat(membershipTable).isEqualTo(1);
    assertThat(permissionTable).isEqualTo(1);
    assertThat(flyway.migrate().migrationsExecuted).isZero();
  }

  @Test
  void rejectsDuplicateMembershipAndUnknownRoles() {
    UUID companyId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO companies (id, name, business_name, email) VALUES (?, ?, 'Company SRL', 'a@b.com')",
        companyId,
        "Company test");
    jdbcTemplate.update(
        "INSERT INTO company_memberships (id, company_id, user_id, role) VALUES (?, ?, ?, ?)",
        UUID.randomUUID(),
        companyId,
        userId,
        "OWNER");

    assertThatThrownBy(() ->
            jdbcTemplate.update(
                "INSERT INTO company_memberships (id, company_id, user_id, role) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(),
                companyId,
                userId,
                "VIEWER"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() ->
            jdbcTemplate.update(
                "INSERT INTO company_memberships (id, company_id, user_id, role) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(),
                companyId,
                UUID.randomUUID(),
                "SUPERUSER"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @Transactional
  void enforcesInitialCompanyConstraints() {
    String rnc = "123456789";

    int inserted =
        jdbcTemplate.update(
            "INSERT INTO companies (id, name, rnc, business_name, email) VALUES (?, ?, ?, 'Empresa SRL', 'a@b.com')",
            UUID.randomUUID(),
            "Empresa de prueba",
            rnc);

    assertThat(inserted).isEqualTo(1);
    assertThatThrownBy(() ->
            jdbcTemplate.update(
                "INSERT INTO companies (id, name, business_name, email) VALUES (?, ?, 'Empresa SRL', 'a@b.com')",
                UUID.randomUUID(),
                " "))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() ->
            jdbcTemplate.update(
                "INSERT INTO companies (id, name, rnc, business_name, email) "
                    + "VALUES (?, ?, ?, 'Empresa SRL', 'a@b.com')",
                UUID.randomUUID(),
                "RNC inválido",
                "12345678A"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() ->
            jdbcTemplate.update(
                "INSERT INTO companies (id, name, rnc, business_name, email) "
                    + "VALUES (?, ?, ?, 'Empresa SRL', 'a@b.com')",
                UUID.randomUUID(),
                "RNC duplicado",
                rnc))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void exposesHealthAndDedicatedAvailabilityProbesWithoutDetails() throws Exception {
    HttpResponse<String> health = get("/actuator/health");
    HttpResponse<String> liveness = get("/actuator/health/liveness");
    HttpResponse<String> readiness = get("/actuator/health/readiness");

    assertThat(health.statusCode()).isEqualTo(200);
    assertThat(health.body()).contains("\"status\":\"UP\"").doesNotContain("components");
    assertThat(liveness.statusCode()).isEqualTo(200);
    assertThat(liveness.body()).contains("\"status\":\"UP\"");
    assertThat(readiness.statusCode()).isEqualTo(200);
    assertThat(readiness.body()).contains("\"status\":\"UP\"");
  }

  @Test
  void exposesOnlySafeBuildInformation() throws Exception {
    HttpResponse<String> info = get("/actuator/info");
    HttpResponse<String> env = get("/actuator/env");
    HttpResponse<String> configprops = get("/actuator/configprops");

    assertThat(info.statusCode()).isEqualTo(200);
    assertThat(info.body())
        .contains("\"name\":\"SaniGest Company Service\"", "\"artifact\":\"company-service\"")
        .doesNotContain("jdbc:", "password", "token");
    assertThat(env.statusCode()).isEqualTo(401);
    assertThat(configprops.statusCode()).isEqualTo(401);
  }

  private HttpResponse<String> get(String path) throws Exception {
    return httpClient.send(
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
        HttpResponse.BodyHandlers.ofString());
  }
}
