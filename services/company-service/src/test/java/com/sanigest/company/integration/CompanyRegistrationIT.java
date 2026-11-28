package com.sanigest.company.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sanigest.company.application.dto.profile.CreateCompanyCommand;
import com.sanigest.company.application.exception.access.CompanyAccessDeniedException;
import com.sanigest.company.application.exception.profile.CompanyConflictException;
import com.sanigest.company.application.usecase.profile.CreateCompanyUseCase;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Tag("regression")
@ActiveProfiles("test")
@Testcontainers
@SpringBootTest
class CompanyRegistrationIT {
  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17.7-alpine").withPassword(UUID.randomUUID().toString());

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    properties.add("spring.datasource.username", POSTGRES::getUsername);
    properties.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private JdbcTemplate jdbc;
  @Autowired private CreateCompanyUseCase useCase;

  @Test
  void persistsCompanyOwnerAndIdempotentResult() {
    var userId = UUID.randomUUID();
    var request =
        new CreateCompanyCommand(
            "Clinic", "Clinic SRL", " ", "a@b.com", "8095555555", "SD", null, null);
    var result = useCase.create(userId, "registration", request);
    assertThat(result.name()).isEqualTo("Clinic");
    assertThat(result.businessName()).isEqualTo("Clinic SRL");
    assertThat(result.email()).isEqualTo("a@b.com");
    assertThat(result.phone()).isEqualTo("8095555555");
    assertThat(result.location()).isEqualTo("SD");
    assertThat(result.rnc()).isNull();
    assertThat(result.currency()).isEqualTo("DOP");
    assertThat(result.status()).isEqualTo("ACTIVE");
    assertThat(
            jdbc.queryForObject(
                "SELECT role FROM company_memberships WHERE user_id=?", String.class, userId))
        .isEqualTo("OWNER");
    assertThat(useCase.create(userId, "registration", request)).isEqualTo(result);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM companies WHERE id = ?", Integer.class, result.id()))
        .isEqualTo(1);
    assertThatThrownBy(() -> useCase.create(userId, "another-key", request))
        .isInstanceOf(CompanyConflictException.class);
    jdbc.update("UPDATE company_memberships SET status = 'REVOKED' WHERE user_id = ?", userId);
    assertThatThrownBy(() -> useCase.create(userId, "registration", request))
        .isInstanceOf(CompanyAccessDeniedException.class);
  }

  @Test
  void forbidsConcurrentSecondMembershipInCompatibilityMode() throws Exception {
    var actor = UUID.randomUUID();
    var command =
        new CreateCompanyCommand(
            "Legacy", "Legacy", null, "company@example.com", "8095555555", "SD", null, null);
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> tryCreate(actor, "first", command, ready, start));
      var second = executor.submit(() -> tryCreate(actor, "second", command, ready, start));
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(
              java.util.List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM company_memberships WHERE user_id = ?", Integer.class, actor))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM company_registration_idempotency WHERE user_id = ?",
                Integer.class,
                actor))
        .isEqualTo(1);
  }

  private boolean tryCreate(
      UUID actor,
      String key,
      CreateCompanyCommand command,
      CountDownLatch ready,
      CountDownLatch start)
      throws InterruptedException {
    ready.countDown();
    assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
    try {
      useCase.create(actor, key, command);
      return true;
    } catch (CompanyConflictException expected) {
      return false;
    }
  }
}
