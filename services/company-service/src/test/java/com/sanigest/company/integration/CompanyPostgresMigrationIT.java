package com.sanigest.company.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Tag("regression")
@Testcontainers
class CompanyPostgresMigrationIT {
  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17.7-alpine")
          .withDatabaseName("sanigest_company")
          .withUsername("sanigest_company")
          .withPassword(UUID.randomUUID().toString());

  @Test
  void appliesCompanyMigrationsToPostgres() throws SQLException {
    Flyway flyway =
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration", "classpath:db/postgresql")
            .load();

    assertThat(flyway.migrate().migrationsExecuted).isEqualTo(14);
    assertThat(flyway.migrate().migrationsExecuted).isZero();
    try (var connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var statement = connection.createStatement()) {
      for (String sql :
          List.of(
              "UPDATE company_audit_log SET result = 'REJECTED'",
              "DELETE FROM company_audit_log",
              "TRUNCATE company_audit_log")) {
        assertThatThrownBy(() -> statement.execute(sql))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("Company audit log is append-only");
      }
    }
  }

  @Test
  void upgradesVersionNineWithExistingCompanyMembershipPermissionAndReplay() {
    String schema = "upgrade";
    var config =
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration", "classpath:db/postgresql")
            .schemas(schema);
    assertThat(config.target("9").load().migrate().migrationsExecuted).isEqualTo(9);

    var jdbc =
        new JdbcTemplate(
            new DriverManagerDataSource(
                POSTGRES.getJdbcUrl() + "?currentSchema=" + schema,
                POSTGRES.getUsername(),
                POSTGRES.getPassword()));
    var actor = UUID.randomUUID();
    var original = UUID.randomUUID();
    var other = UUID.randomUUID();
    var membership = UUID.randomUUID();
    var permission = UUID.randomUUID();
    for (var company : List.of(original, other)) {
      jdbc.update(
          "INSERT INTO companies (id, name, business_name, email) VALUES (?, 'Company', 'Company', 'a@b.com')",
          company);
    }
    jdbc.update(
        "INSERT INTO company_memberships (id, company_id, user_id, role) VALUES (?, ?, ?, 'OWNER')",
        membership,
        original,
        actor);
    jdbc.update(
        "INSERT INTO company_permissions (id, company_id, permission) VALUES (?, ?, 'CATALOG_READ')",
        permission,
        original);
    jdbc.update("INSERT INTO membership_permissions VALUES (?, ?)", membership, permission);
    jdbc.update(
        "INSERT INTO company_registration_idempotency (user_id, idempotency_key, request_hash, company_id) "
            + "VALUES (?, 'original', ?, ?)",
        actor,
        "a".repeat(64),
        original);

    assertThat(config.target("10").load().migrate().migrationsExecuted).isEqualTo(1);
    UUID invitation = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO company_invitations
          (id, company_id, email, role, token_hash, status, pending_slot, created_at, expires_at)
        VALUES (?, ?, 'guest@example.com', 'VIEWER', ?, 'PENDING', 1,
            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '1 hour')
        """,
        invitation,
        original,
        "b".repeat(64));
    assertThat(config.target("12").load().migrate().migrationsExecuted).isEqualTo(2);
    assertThat(
            jdbc.queryForMap(
                "SELECT recipient_name, email, status FROM company_invitations WHERE id = ?",
                invitation))
        .containsEntry("recipient_name", null)
        .containsEntry("email", "guest@example.com")
        .containsEntry("status", "PENDING");
    jdbc.update(
        "INSERT INTO company_memberships (id, company_id, user_id, role) VALUES (?, ?, ?, 'VIEWER')",
        UUID.randomUUID(),
        other,
        actor);
    assertThat(
            jdbc.queryForMap(
                "SELECT company_id, user_id, role, status FROM company_memberships WHERE id = ?",
                membership))
        .containsEntry("company_id", original)
        .containsEntry("user_id", actor)
        .containsEntry("role", "OWNER")
        .containsEntry("status", "ACTIVE");
    assertThat(
            jdbc.queryForObject(
                "SELECT permission_id FROM membership_permissions WHERE membership_id = ?",
                UUID.class,
                membership))
        .isEqualTo(permission);
    assertThat(
            jdbc.queryForMap(
                "SELECT company_id, request_hash FROM company_registration_idempotency "
                    + "WHERE user_id = ? AND idempotency_key = 'original'",
                actor))
        .containsEntry("company_id", original)
        .containsEntry("request_hash", "a".repeat(64));
    assertThatThrownBy(() ->
            jdbc.update(
                "INSERT INTO company_memberships (id, company_id, user_id, role) VALUES (?, ?, ?, 'VIEWER')",
                UUID.randomUUID(),
                original,
                actor))
        .isInstanceOf(DuplicateKeyException.class);
  }
}
