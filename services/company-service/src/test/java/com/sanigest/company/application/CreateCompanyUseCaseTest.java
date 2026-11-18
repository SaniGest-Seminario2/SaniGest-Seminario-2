package com.sanigest.company.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sanigest.company.api.CompanyController.CompanyRequest;
import com.sanigest.company.api.CompanyController.CompanyResponse;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

class CreateCompanyUseCaseTest {
  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID COMPANY_ID = UUID.randomUUID();
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final CreateCompanyUseCase useCase = new CreateCompanyUseCase(jdbc);
  private final CompanyRequest request =
      new CompanyRequest(
          "Clínica", "Clínica SRL", "123456789", "a@b.com", "8095555555", "SD", null, null);

  @BeforeEach
  void noExistingRegistration() {
    when(jdbc.query(anyString(), any(ResultSetExtractor.class), eq(USER_ID), anyString()))
        .thenReturn(null);
    when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(USER_ID))).thenReturn(0);
  }

  @Test
  void rejectsMissingOrOversizedIdempotencyKey() {
    assertThatThrownBy(() -> useCase.create(USER_ID, " ", request))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> useCase.create(USER_ID, "x".repeat(129), request))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsAlreadyRegisteredUser() {
    when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(USER_ID))).thenReturn(1);
    assertThatThrownBy(() -> useCase.create(USER_ID, "key", request))
        .isInstanceOf(CreateCompanyUseCase.ConflictException.class);
  }

  @Test
  void rejectsDuplicateKeyWithDifferentBody() {
    when(jdbc.query(anyString(), any(ResultSetExtractor.class), eq(USER_ID), eq("key")))
        .thenReturn(new CreateCompanyUseCase.Prior("different", COMPANY_ID));
    assertThatThrownBy(() -> useCase.create(USER_ID, "key", request))
        .isInstanceOf(CreateCompanyUseCase.ConflictException.class);
  }

  @Test
  void returnsPreviousResultForIdenticalRetry() throws Exception {
    String hash =
        java.util.HexFormat.of()
            .formatHex(
                java.security.MessageDigest.getInstance("SHA-256")
                    .digest(
                        String.join(
                                "\u0000",
                                "Clínica",
                                "Clínica SRL",
                                "123456789",
                                "a@b.com",
                                "8095555555",
                                "SD",
                                "null",
                                "null")
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    when(jdbc.query(anyString(), any(ResultSetExtractor.class), eq(USER_ID), eq("key")))
        .thenReturn(new CreateCompanyUseCase.Prior(hash, COMPANY_ID));
    when(jdbc.queryForObject(anyString(), any(RowMapper.class), eq(COMPANY_ID)))
        .thenReturn(
            new CompanyResponse(
                COMPANY_ID,
                "Clínica",
                "Clínica SRL",
                "123456789",
                "a@b.com",
                "8095555555",
                "SD",
                "DOP",
                "ACTIVE"));
    assertThat(useCase.create(USER_ID, "key", request).companyId()).isEqualTo(COMPANY_ID);
  }

  @Test
  void mapsDatabaseUniquenessFailureToConflict() {
    when(jdbc.update(anyString(), any(Object[].class)))
        .thenThrow(new DataIntegrityViolationException("duplicate"));
    assertThatThrownBy(() -> useCase.create(USER_ID, "key", request))
        .isInstanceOf(CreateCompanyUseCase.ConflictException.class);
  }
}
