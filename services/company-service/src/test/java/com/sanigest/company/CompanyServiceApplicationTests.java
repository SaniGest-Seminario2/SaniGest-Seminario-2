package com.sanigest.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
class CompanyServiceApplicationTests {

  @Autowired private Flyway flyway;

  @Autowired private JdbcTemplate jdbcTemplate;

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
    assertThat(flyway.migrate().migrationsExecuted).isZero();
  }

  @Test
  @Transactional
  void enforcesInitialCompanyConstraints() {
    String rnc = "123456789";

    int inserted =
        jdbcTemplate.update(
            "INSERT INTO companies (id, name, rnc) VALUES (?, ?, ?)",
            UUID.randomUUID(),
            "Empresa de prueba",
            rnc);

    assertThat(inserted).isEqualTo(1);
    assertThatThrownBy(() ->
            jdbcTemplate.update(
                "INSERT INTO companies (id, name) VALUES (?, ?)", UUID.randomUUID(), " "))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() ->
            jdbcTemplate.update(
                "INSERT INTO companies (id, name, rnc) VALUES (?, ?, ?)",
                UUID.randomUUID(),
                "RNC inválido",
                "12345678A"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() ->
            jdbcTemplate.update(
                "INSERT INTO companies (id, name, rnc) VALUES (?, ?, ?)",
                UUID.randomUUID(),
                "RNC duplicado",
                rnc))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
