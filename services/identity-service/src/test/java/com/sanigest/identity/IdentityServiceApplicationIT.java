package com.sanigest.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sanigest.identity.application.DuplicateEmailException;
import com.sanigest.identity.application.RegisterUserCommand;
import com.sanigest.identity.application.RegisterUserService;
import com.sanigest.identity.domain.model.User;
import com.sanigest.identity.domain.port.PasswordHasher;
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
class IdentityServiceApplicationIT {

  @Autowired private Flyway flyway;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private MockMvc mockMvc;
  @Autowired private RegisterUserService registerUserService;
  @Autowired private PasswordHasher passwordHasher;

  @Test
  void appliesMigrationAndKeepsItIdempotent() {
    Integer applied =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM "flyway_schema_history"
            WHERE "version" = '1' AND "success" = TRUE
            """,
            Integer.class);

    assertThat(applied).isEqualTo(1);
    assertThat(flyway.migrate().migrationsExecuted).isZero();
  }

  @Test
  @Transactional
  void persistsAUserWithoutPersistingThePlainTextPassword() {
    String rawPassword = "correct-horse-battery-staple";

    User user =
        registerUserService.register(
            new RegisterUserCommand(" Test.User@Example.COM ", rawPassword, "Test User"));

    String storedEmail =
        jdbcTemplate.queryForObject(
            "SELECT email FROM users WHERE id = ?", String.class, user.id());
    String storedHash =
        jdbcTemplate.queryForObject(
            "SELECT password_hash FROM users WHERE id = ?", String.class, user.id());
    assertThat(storedEmail).isEqualTo("test.user@example.com");
    assertThat(storedHash).isNotEqualTo(rawPassword).startsWith("$2");
    assertThat(passwordHasher.matches(rawPassword, storedHash)).isTrue();
  }

  @Test
  @Transactional
  void treatsDifferentlyCasedEmailsAsTheSameAccount() {
    registerUserService.register(
        new RegisterUserCommand("unique@example.com", "a-secure-password", "First"));

    assertThatThrownBy(() ->
            registerUserService.register(
                new RegisterUserCommand("UNIQUE@EXAMPLE.COM", "another-password", "Second")))
        .isInstanceOf(DuplicateEmailException.class);
  }

  @Test
  @Transactional
  void databaseRejectsNonNormalizedEmailEvenWhenBypassingTheApplication() {
    assertThatThrownBy(() ->
            jdbcTemplate.update(
                """
                INSERT INTO users
                  (id, email, password_hash, name, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                UUID.randomUUID(),
                "Upper@Example.com",
                "$2a$12$not-a-real-password-hash",
                "Bypass",
                "ACTIVE"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void exposesOnlySafeOperationalEndpoints() throws Exception {
    mockMvc
        .perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"))
        .andExpect(jsonPath("$.components").doesNotExist());
    mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
    mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    mockMvc
        .perform(get("/actuator/info"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.build.artifact").value("identity-service"))
        .andExpect(content().string(not(containsString("password"))));
    mockMvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
  }
}
