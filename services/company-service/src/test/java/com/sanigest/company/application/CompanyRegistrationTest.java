package com.sanigest.company.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sanigest.company.api.CompanyController.CompanyRequest;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class CompanyRegistrationTest {
  @Test
  void persistsCompanyOwnerAndIdempotentResult() {
    var dataSource =
        new SingleConnectionDataSource(
            "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "sa",
            "",
            true);
    Flyway.configure().dataSource(dataSource).load().migrate();
    var jdbc = new JdbcTemplate(dataSource);
    var useCase = new CreateCompanyUseCase(jdbc);
    var userId = UUID.randomUUID();
    var request =
        new CompanyRequest("Clinic", "Clinic SRL", " ", "a@b.com", "8095555555", "SD", null, null);
    var result = useCase.create(userId, "registration", request);
    assertThat(result.companyName()).isEqualTo("Clinic");
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
        .isInstanceOf(CreateCompanyUseCase.ConflictException.class);
  }
}
