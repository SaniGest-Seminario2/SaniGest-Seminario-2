package com.sanigest.company.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sanigest.company.application.dto.CreateCompanyCommand;
import com.sanigest.company.application.exception.CompanyAccessDeniedException;
import com.sanigest.company.application.exception.CompanyConflictException;
import com.sanigest.company.application.usecase.profile.CreateCompanyUseCase;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@Tag("regression")
@ActiveProfiles("test")
@SpringBootTest(
    properties =
        "spring.datasource.url=jdbc:h2:mem:company-registration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class CompanyRegistrationIT {
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
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM companies", Integer.class)).isEqualTo(1);
    assertThatThrownBy(() -> useCase.create(userId, "another-key", request))
        .isInstanceOf(CompanyConflictException.class);
    jdbc.update("UPDATE company_memberships SET status = 'REVOKED' WHERE user_id = ?", userId);
    assertThatThrownBy(() -> useCase.create(userId, "registration", request))
        .isInstanceOf(CompanyAccessDeniedException.class);
  }
}
