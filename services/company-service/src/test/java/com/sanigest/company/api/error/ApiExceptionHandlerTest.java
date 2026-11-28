package com.sanigest.company.api.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sanigest.company.api.routing.CompanyRoutes;
import com.sanigest.company.application.exception.team.MemberNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
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

  @Test
  void serializesBadRequestAndNotFoundWithTheSharedProblemFields() throws Exception {
    var mvc =
        MockMvcBuilders.standaloneSetup(new MemberEndpoint())
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    mvc.perform(get("/members").param("page", "invalid"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:sanigest:problem:invalid_request"))
        .andExpect(jsonPath("$.title").value("Bad Request"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.instance").value("/members"))
        .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.detail").isNotEmpty())
        .andExpect(jsonPath("$.timestamp").isString())
        .andExpect(jsonPath("$.traceId").isString());
    mvc.perform(get("/members").param("page", "0"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
  }

  @RestController
  static class MemberEndpoint {
    @GetMapping("/members")
    void find(@RequestParam int page) {
      throw new MemberNotFoundException();
    }
  }
}
