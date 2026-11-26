package com.sanigest.security.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

class MvcProblemsTest {
  private static final String TRACE = "0123456789abcdef0123456789abcdef";
  private final Advice advice = new Advice();
  private final MockMvc mvc =
      MockMvcBuilders.standaloneSetup(new Endpoint()).setControllerAdvice(advice).build();

  @Test
  void identifiesMissingParametersAndHeadersAndAnInvalidUuid() throws Exception {
    mvc.perform(get("/lookup"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.id").value("Required parameter"));
    mvc.perform(get("/lookup").param("id", "invalid"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.id").value("Invalid value"));
    mvc.perform(get("/lookup").param("id", "c1bde7ca-4ca6-4a42-9dad-98841d88dd95"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors['X-Required']").value("Required header"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "{", "{\"lines\":[{\"id\":\"invalid\"}]}"})
  void rejectsUnreadableInput(String payload) throws Exception {
    var result =
        mvc.perform(post("/lookup").contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.errorCode").value("INVALID_LOOKUP"));
    if (payload.contains("lines")) {
      result.andExpect(jsonPath("$.errors['lines[0].id']").value("Invalid value"));
    }
  }

  @Test
  void addsMetadataAndRedactsTheInvitationPathWhilePreservingTheBusinessCode() throws Exception {
    mvc.perform(get(URI.create("/api/v1/%69nvitations/secret/accept")).header("X-Trace-Id", TRACE))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.instance").value("/api/v1/invitations"))
        .andExpect(jsonPath("$.traceId").value(TRACE))
        .andExpect(jsonPath("$.type").value("urn:sanigest:problem:invitation_closed"))
        .andExpect(jsonPath("$.errorCode").value("INVITATION_CLOSED"))
        .andExpect(jsonPath("$.detail").isNotEmpty());
  }

  @Test
  void preservesAllowAndKeepsInternalErrorsSafe() throws Exception {
    mvc.perform(post("/failure"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(header().exists("Allow"))
        .andExpect(jsonPath("$.errorCode").value("METHOD_NOT_ALLOWED"));
    var failure =
        mvc.perform(get("/failure"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
            .andReturn();
    assertThat(failure.getResponse().getContentAsString()).doesNotContain("sensitive message");
    var denied = new AccessDeniedException("denied");
    assertThatThrownBy(() -> advice.denied(denied)).isSameAs(denied);
  }

  @RestControllerAdvice
  static class Advice extends MvcProblems {
    Advice() {
      super("INVALID_LOOKUP");
    }
  }

  @RestController
  static class Endpoint {
    @GetMapping("/lookup")
    String lookup(@RequestParam UUID id, @RequestHeader("X-Required") String required) {
      return id.toString();
    }

    @PostMapping("/lookup")
    Payload lookup(@RequestBody Payload payload) {
      return payload;
    }

    @GetMapping("/api/v1/invitations/{token}/accept")
    ProblemDetail invitation() {
      var problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
      problem.setProperty("errorCode", "INVITATION_CLOSED");
      return problem;
    }

    @GetMapping("/failure")
    String failure() {
      throw new IllegalStateException("sensitive message");
    }
  }

  record Payload(List<Line> lines) {}

  record Line(UUID id) {}
}
