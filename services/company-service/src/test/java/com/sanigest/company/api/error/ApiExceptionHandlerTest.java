package com.sanigest.company.api.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.sanigest.company.api.routing.CompanyRoutes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

class ApiExceptionHandlerTest {
  @Test
  void returnsSafeProblemDetails() {
    var handler = new ApiExceptionHandler();
    var request = new MockHttpServletRequest();
    assertThat(handler.badRequest(request).getStatus()).isEqualTo(400);
    assertThat(handler.companyAccessDenied(request).getStatus()).isEqualTo(403);
    assertThat(handler.authorizationUnavailable(request).getStatus()).isEqualTo(503);
    assertThat(handler.conflict(request).getStatus()).isEqualTo(409);
    assertThat(handler.conflict(request).getDetail()).doesNotContain("SQL", "password");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"/api/v1/invitations/secret/accept", "/api/v1/%69nvitations/secret/accept"})
  void redactsTheTokenFromInvitationProblemDetails(String uri) {
    var request = new MockHttpServletRequest("POST", uri);
    request.setAttribute(
        HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, CompanyRoutes.ACCEPT_INVITATION);

    var problem = new ApiExceptionHandler().invitationNotFound(request);

    assertThat(problem.getStatus()).isEqualTo(404);
    assertThat(problem.getInstance()).hasToString("/api/v1/invitations");
  }
}
