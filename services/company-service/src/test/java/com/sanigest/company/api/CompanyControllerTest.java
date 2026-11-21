package com.sanigest.company.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sanigest.company.application.CreateCompanyUseCase;
import com.sanigest.company.application.GetMyCompanyUseCase;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

class CompanyControllerTest {
  @Test
  void createsCompanyForAuthenticatedUser() {
    var useCase = mock(CreateCompanyUseCase.class);
    var userId = UUID.randomUUID();
    var request =
        new CompanyController.CompanyRequest(
            "Clinic", "Clinic SRL", null, "a@b.com", "8095555555", "SD", null, null);
    var response =
        new CompanyController.CompanyResponse(
            UUID.randomUUID(),
            "Clinic",
            "Clinic SRL",
            null,
            "a@b.com",
            "8095555555",
            "SD",
            "DOP",
            "ACTIVE");
    when(useCase.create(userId, "key", request)).thenReturn(response);
    var jwt = Jwt.withTokenValue("test").header("alg", "HS256").subject(userId.toString()).build();
    var result =
        new CompanyController(useCase, mock(GetMyCompanyUseCase.class)).create(jwt, "key", request);
    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(result.getBody()).isEqualTo(response);
  }

  @Test
  void rejectsInvalidAuthenticatedIdentity() {
    var controller =
        new CompanyController(mock(CreateCompanyUseCase.class), mock(GetMyCompanyUseCase.class));
    var jwt = Jwt.withTokenValue("test").header("alg", "HS256").subject("invalid").build();
    assertThatThrownBy(() -> controller.create(jwt, "key", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid authenticated identity");
  }

  @Test
  void returnsSafeProblemDetails() {
    var handler = new ApiExceptionHandler();
    assertThat(handler.validation().getStatus()).isEqualTo(400);
    assertThat(handler.badRequest().getStatus()).isEqualTo(400);
    assertThat(handler.conflict().getStatus()).isEqualTo(409);
    assertThat(handler.conflict().getDetail()).doesNotContain("SQL", "password");
  }
}
