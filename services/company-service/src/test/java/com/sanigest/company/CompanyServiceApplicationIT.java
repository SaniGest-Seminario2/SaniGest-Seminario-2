package com.sanigest.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CompanyServiceApplicationIT {

  @Autowired private Flyway flyway;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private MockMvc mockMvc;

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

  @Test
  void exposesHealthAndDedicatedAvailabilityProbesWithoutDetails() throws Exception {
    mockMvc
        .perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"))
        .andExpect(jsonPath("$.components").doesNotExist());

    mockMvc
        .perform(get("/actuator/health/liveness"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));

    mockMvc
        .perform(get("/actuator/health/readiness"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void exposesOnlySafeBuildInformation() throws Exception {
    mockMvc
        .perform(get("/actuator/info"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.build.name").value("SaniGest Company Service"))
        .andExpect(jsonPath("$.build.artifact").value("company-service"))
        .andExpect(jsonPath("$.build.version").isNotEmpty())
        .andExpect(content().string(not(containsString("jdbc:"))))
        .andExpect(content().string(not(containsString("password"))))
        .andExpect(content().string(not(containsString("token"))));

    mockMvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
    mockMvc.perform(get("/actuator/configprops")).andExpect(status().isNotFound());
  }
}
