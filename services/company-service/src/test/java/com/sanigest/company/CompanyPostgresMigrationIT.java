package com.sanigest.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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
          .withPassword("test-only-password");

  @Test
  void appliesCompanyMigrationsToPostgres() throws SQLException {
    Flyway flyway =
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration", "classpath:db/postgresql")
            .load();

    assertThat(flyway.migrate().migrationsExecuted).isEqualTo(6);
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
}
