package com.sanigest.company.application.usecase.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sanigest.company.application.dto.CompanyRegistration;
import com.sanigest.company.application.dto.CreateCompanyCommand;
import com.sanigest.company.application.exception.CompanyConflictException;
import com.sanigest.company.application.port.out.persistence.CompanyRegistrationStore;
import com.sanigest.company.domain.model.Company;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CreateCompanyUseCaseTest {
  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID COMPANY_ID = UUID.randomUUID();
  private final CompanyRegistrationStore registrations = mock(CompanyRegistrationStore.class);
  private final CreateCompanyUseCase useCase = new CreateCompanyUseCase(registrations);
  private final CreateCompanyCommand request =
      new CreateCompanyCommand(
          "Clínica", "Clínica SRL", "123456789", "a@b.com", "8095555555", "SD", null, null);

  @Test
  void storesNewCompanyWithItsIdempotencyRecord() {
    useCase.create(USER_ID, "key", request);

    var registration = ArgumentCaptor.forClass(CompanyRegistration.class);
    var company = ArgumentCaptor.forClass(Company.class);
    verify(registrations).insert(eq(USER_ID), eq("key"), registration.capture(), company.capture());
    assertThat(registration.getValue().hash()).hasSize(64);
    assertThat(company.getValue().rnc()).isEqualTo(request.rnc());
    assertThat(company.getValue().id()).isEqualTo(registration.getValue().companyId());
    verify(registrations).loadCompany(company.getValue().id());
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
    when(registrations.hasMembership(USER_ID)).thenReturn(true);
    assertThatThrownBy(() -> useCase.create(USER_ID, "key", request))
        .isInstanceOf(CompanyConflictException.class);
  }

  @Test
  void rejectsDuplicateKeyWithDifferentBody() {
    when(registrations.lockActiveMembership(USER_ID, COMPANY_ID)).thenReturn(true);
    when(registrations.find(USER_ID, "key"))
        .thenReturn(Optional.of(new CompanyRegistration("different", COMPANY_ID)));
    assertThatThrownBy(() -> useCase.create(USER_ID, "key", request))
        .isInstanceOf(CompanyConflictException.class);
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
    when(registrations.lockActiveMembership(USER_ID, COMPANY_ID)).thenReturn(true);
    when(registrations.find(USER_ID, "key"))
        .thenReturn(Optional.of(new CompanyRegistration(hash, COMPANY_ID)));
    when(registrations.loadCompany(COMPANY_ID))
        .thenReturn(
            new Company(
                COMPANY_ID,
                "Clínica",
                "Clínica SRL",
                "123456789",
                "a@b.com",
                "8095555555",
                "SD",
                "DOP",
                "ACTIVE"));
    assertThat(useCase.create(USER_ID, "key", request).id()).isEqualTo(COMPANY_ID);
  }
}
