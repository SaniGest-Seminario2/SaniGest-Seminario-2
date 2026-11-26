package com.sanigest.company.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sanigest.company.api.dto.CompanyProfileResponse;
import com.sanigest.company.api.dto.CompanyRequest;
import com.sanigest.company.api.dto.CompanyResponse;
import com.sanigest.company.api.dto.CompanySummaryResponse;
import com.sanigest.company.application.account.AccountType;
import com.sanigest.company.application.dto.AuthorizedCompany;
import com.sanigest.company.application.dto.CompanyProfile;
import com.sanigest.company.application.dto.CreateCompanyCommand;
import com.sanigest.company.application.exception.AuthorizationUnavailableException;
import com.sanigest.company.application.exception.CompanyAccessDeniedException;
import com.sanigest.company.application.port.out.integration.CurrentAccount;
import com.sanigest.company.application.usecase.access.RequireRegularAccount;
import com.sanigest.company.application.usecase.profile.CreateCompanyUseCase;
import com.sanigest.company.application.usecase.profile.GetMyCompanyUseCase;
import com.sanigest.company.application.usecase.profile.ListMyCompaniesUseCase;
import com.sanigest.company.application.usecase.profile.UpdateMyCompanyUseCase;
import com.sanigest.company.domain.enums.CompanyRole;
import com.sanigest.company.domain.enums.MembershipStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

class CompanyControllerTest {
  @Test
  void mapsCompanyProfileToItsHttpContract() {
    var profiles = mock(GetMyCompanyUseCase.class);
    var userId = UUID.randomUUID();
    var companyId = UUID.randomUUID();
    when(profiles.get(userId, null))
        .thenReturn(
            new CompanyProfile(
                companyId, "Clinic", "Clinic SRL", "123456789", "SD", "DOP", "ACTIVE", null, null));
    var controller =
        new CompanyController(
            mock(CreateCompanyUseCase.class),
            profiles,
            mock(UpdateMyCompanyUseCase.class),
            mock(ListMyCompaniesUseCase.class),
            new RequireRegularAccount(token -> AccountType.REGULAR));
    var jwt = Jwt.withTokenValue("test").header("alg", "RS256").subject(userId.toString()).build();

    assertThat(controller.me(jwt, null, request()))
        .isEqualTo(
            new CompanyProfileResponse(
                companyId, "Clinic", "Clinic SRL", "123456789", "SD", "DOP", "ACTIVE", null, null));
  }

  @Test
  void createsCompanyForAuthenticatedUser() {
    var useCase = mock(CreateCompanyUseCase.class);
    var userId = UUID.randomUUID();
    var request =
        new CompanyRequest("Clinic", "Clinic SRL", null, "a@b.com", "8095555555", "SD", null, null);
    var response =
        new CompanyResponse(
            UUID.randomUUID(),
            "Clinic",
            "Clinic SRL",
            null,
            "a@b.com",
            "8095555555",
            "SD",
            "DOP",
            "ACTIVE");
    when(useCase.create(eq(userId), eq("key"), any(CreateCompanyCommand.class)))
        .thenReturn(
            new com.sanigest.company.domain.model.Company(
                response.companyId(),
                response.companyName(),
                response.businessName(),
                response.rnc(),
                response.email(),
                response.phone(),
                response.location(),
                response.currency(),
                response.status()));
    var jwt = Jwt.withTokenValue("test").header("alg", "RS256").subject(userId.toString()).build();
    var result =
        new CompanyController(
                useCase,
                mock(GetMyCompanyUseCase.class),
                mock(UpdateMyCompanyUseCase.class),
                mock(ListMyCompaniesUseCase.class),
                new RequireRegularAccount(token -> AccountType.REGULAR))
            .create(jwt, "key", request);
    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(result.getBody()).isEqualTo(response);
  }

  @Test
  void mapsAuthorizedCompaniesAndEmptyLists() {
    var list = mock(ListMyCompaniesUseCase.class);
    var userId = UUID.randomUUID();
    var companyId = UUID.randomUUID();
    var jwt = Jwt.withTokenValue("test").header("alg", "RS256").subject(userId.toString()).build();
    var controller =
        new CompanyController(
            mock(CreateCompanyUseCase.class),
            mock(GetMyCompanyUseCase.class),
            mock(UpdateMyCompanyUseCase.class),
            list,
            new RequireRegularAccount(token -> AccountType.REGULAR));

    when(list.list(userId)).thenReturn(List.of());
    assertThat(controller.list(jwt)).isEmpty();
    when(list.list(userId))
        .thenReturn(
            List.of(
                new AuthorizedCompany(
                    companyId,
                    "Clinic SRL",
                    "Clinic",
                    CompanyRole.VIEWER,
                    MembershipStatus.ACTIVE,
                    "ACTIVE")));
    assertThat(controller.list(jwt))
        .containsExactly(
            new CompanySummaryResponse(
                companyId,
                "Clinic SRL",
                "Clinic",
                CompanyRole.VIEWER,
                MembershipStatus.ACTIVE,
                "ACTIVE"));
  }

  @Test
  void rejectsInvalidAuthenticatedIdentity() {
    var controller =
        new CompanyController(
            mock(CreateCompanyUseCase.class),
            mock(GetMyCompanyUseCase.class),
            mock(UpdateMyCompanyUseCase.class),
            mock(ListMyCompaniesUseCase.class),
            new RequireRegularAccount(mock(CurrentAccount.class)));
    var jwt = Jwt.withTokenValue("test").header("alg", "RS256").subject("invalid").build();
    assertThatThrownBy(() -> controller.create(jwt, "key", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid authenticated identity");
  }

  @Test
  void requiresLiveAuthorizationBeforeInvokingAnyCompanyOperation() {
    var create = mock(CreateCompanyUseCase.class);
    var read = mock(GetMyCompanyUseCase.class);
    var update = mock(UpdateMyCompanyUseCase.class);
    var list = mock(ListMyCompaniesUseCase.class);
    var accounts = mock(CurrentAccount.class);
    var controller =
        new CompanyController(create, read, update, list, new RequireRegularAccount(accounts));
    var jwt =
        Jwt.withTokenValue("test")
            .header("alg", "RS256")
            .subject(UUID.randomUUID().toString())
            .claim("accountType", "REGULAR")
            .build();
    when(accounts.resolve("test")).thenReturn(AccountType.ADVISOR);
    assertThatThrownBy(() -> controller.me(jwt, null, request()))
        .isInstanceOf(CompanyAccessDeniedException.class);
    assertThatThrownBy(() -> controller.update(jwt, null, request(), null))
        .isInstanceOf(CompanyAccessDeniedException.class);
    assertThatThrownBy(() -> controller.create(jwt, "key", null))
        .isInstanceOf(CompanyAccessDeniedException.class);
    assertThatThrownBy(() -> controller.list(jwt)).isInstanceOf(CompanyAccessDeniedException.class);
    when(accounts.resolve("test")).thenThrow(new AuthorizationUnavailableException());
    assertThatThrownBy(() -> controller.me(jwt, null, request()))
        .isInstanceOf(AuthorizationUnavailableException.class);
    assertThatThrownBy(() -> controller.list(jwt))
        .isInstanceOf(AuthorizationUnavailableException.class);
    verifyNoInteractions(create, read, update, list);
  }

  private static org.springframework.mock.web.MockHttpServletRequest request() {
    return new org.springframework.mock.web.MockHttpServletRequest();
  }
}
