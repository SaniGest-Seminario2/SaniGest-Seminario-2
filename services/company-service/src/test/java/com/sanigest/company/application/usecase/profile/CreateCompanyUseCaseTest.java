package com.sanigest.company.application.usecase.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sanigest.company.application.dto.profile.CompanyRegistration;
import com.sanigest.company.application.dto.profile.CreateCompanyCommand;
import com.sanigest.company.application.exception.access.CompanyAccessDeniedException;
import com.sanigest.company.application.exception.profile.CompanyConflictException;
import com.sanigest.company.application.port.out.persistence.profile.CompanyRegistrationStore;
import com.sanigest.company.domain.enums.team.CompanyRole;
import com.sanigest.company.domain.enums.team.MembershipStatus;
import com.sanigest.company.domain.model.profile.Company;
import com.sanigest.company.domain.model.team.Membership;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CreateCompanyUseCaseTest {
  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID COMPANY_ID = UUID.randomUUID();
  private final CompanyRegistrationStore registrations = mock(CompanyRegistrationStore.class);
  private final CreateCompanyUseCase useCase = new CreateCompanyUseCase(registrations, true);
  private final CreateCompanyCommand request =
      new CreateCompanyCommand(
          "Clínica", "Clínica SRL", "123456789", "a@b.com", "8095555555", "SD", null, null);

  @Test
  void storesNewCompanyWithItsIdempotencyRecord() {
    useCase.create(USER_ID, "key", request);

    var hash = ArgumentCaptor.forClass(String.class);
    var company = ArgumentCaptor.forClass(Company.class);
    var owner = ArgumentCaptor.forClass(Membership.class);
    verify(registrations).insert(eq("key"), hash.capture(), company.capture(), owner.capture());
    assertThat(hash.getValue()).hasSize(64);
    assertThat(owner.getValue().userId()).isEqualTo(USER_ID);
    assertThat(owner.getValue().role()).isEqualTo(CompanyRole.OWNER);
    assertThat(owner.getValue().status()).isEqualTo(MembershipStatus.ACTIVE);
    assertThat(company.getValue().currency()).isEqualTo("DOP");
    assertThat(company.getValue().status()).isEqualTo("ACTIVE");
    assertThat(company.getValue().rnc()).isEqualTo(request.rnc());
    assertThat(company.getValue().id()).isEqualTo(owner.getValue().companyId());
    verify(registrations).loadCompany(company.getValue().id());
  }

  @Test
  void rejectsMissingOrOversizedIdempotencyKey() {
    assertThatThrownBy(() -> useCase.create(USER_ID, " ", request))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> useCase.create(USER_ID, "x\u0000y", request))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> useCase.create(USER_ID, "x".repeat(129), request))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsAlreadyRegisteredUserInCompatibilityMode() {
    var legacy = new CreateCompanyUseCase(registrations, false);
    when(registrations.hasMembership(USER_ID)).thenReturn(true);
    assertThatThrownBy(() -> legacy.create(USER_ID, "key", request))
        .isInstanceOf(CompanyConflictException.class);
  }

  @Test
  void allowsAdditionalCompaniesInMultiCompanyMode() {
    when(registrations.hasMembership(USER_ID)).thenReturn(true);
    useCase.create(USER_ID, "first", request);
    useCase.create(USER_ID, "second", request);

    var first = ArgumentCaptor.forClass(Company.class);
    var second = ArgumentCaptor.forClass(Company.class);
    verify(registrations).insert(eq("first"), anyString(), first.capture(), any());
    verify(registrations).insert(eq("second"), anyString(), second.capture(), any());
    assertThat(first.getValue().id()).isNotEqualTo(second.getValue().id());
    verify(registrations, never()).hasMembership(USER_ID);
  }

  @Test
  void rejectsReplayWhenOriginalMembershipIsNoLongerActive() {
    when(registrations.find(USER_ID, "key"))
        .thenReturn(Optional.of(new CompanyRegistration("stored", COMPANY_ID)));
    assertThatThrownBy(() -> useCase.create(USER_ID, "key", request))
        .isInstanceOf(CompanyAccessDeniedException.class);
    verify(registrations, never()).loadCompany(COMPANY_ID);
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
