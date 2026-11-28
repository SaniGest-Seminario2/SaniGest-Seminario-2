package com.sanigest.security.api.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.ExceptionHandlingConfigurer;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.json.JsonMapper;

class ServletSecurityProblemsTest {
  private final JsonMapper json = JsonMapper.builder().build();
  private final ServletSecurityProblems problems = new ServletSecurityProblems(json, "NO_ACCESS");
  private final ArgumentCaptor<AuthenticationEntryPoint> entry =
      ArgumentCaptor.forClass(AuthenticationEntryPoint.class);
  private final ArgumentCaptor<AccessDeniedHandler> denied =
      ArgumentCaptor.forClass(AccessDeniedHandler.class);

  @BeforeEach
  void configureResourceServer() {
    OAuth2ResourceServerConfigurer<HttpSecurity> resource = mock();
    when(resource.authenticationEntryPoint(any())).thenReturn(resource);
    problems.configure(resource);
    verify(resource).authenticationEntryPoint(entry.capture());
    verify(resource).accessDeniedHandler(denied.capture());
  }

  @Test
  void invalidTokenPreservesTheChallengeAndRedactsTheDecoderMessage() throws Exception {
    var request = new MockHttpServletRequest("GET", "/resource");
    var response = new MockHttpServletResponse();
    entry
        .getValue()
        .commence(request, response, new InvalidBearerTokenException("sensitive token"));

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE))
        .isEqualTo("Bearer error=\"invalid_token\"");
    assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    var body = json.readTree(response.getContentAsByteArray());
    assertThat(body.path("errorCode").asString()).isEqualTo("AUTHENTICATION_REQUIRED");
    assertThat(body.path("traceId").asString()).matches("[0-9a-f]{32}");
    assertThat(response.getContentAsString()).doesNotContain("sensitive token");
  }

  @Test
  void configuresServletExceptionHandlingAndKeepsHeadBodyEmpty() throws Exception {
    HttpSecurity http = mock(HttpSecurity.class);
    ExceptionHandlingConfigurer<HttpSecurity> exceptions = mock();
    when(exceptions.authenticationEntryPoint(any())).thenReturn(exceptions);
    doAnswer(
            invocation -> {
              invocation
                  .<Customizer<ExceptionHandlingConfigurer<HttpSecurity>>>getArgument(0)
                  .customize(exceptions);
              return http;
            })
        .when(http)
        .exceptionHandling(any());
    problems.configure(http);
    verify(exceptions).authenticationEntryPoint(entry.capture());
    verify(exceptions).accessDeniedHandler(denied.capture());

    var response = new MockHttpServletResponse();
    entry
        .getValue()
        .commence(
            new MockHttpServletRequest("HEAD", "/resource"),
            response,
            new InsufficientAuthenticationException("missing"));
    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Bearer");
    assertThat(response.getContentAsByteArray()).isEmpty();
  }

  @Test
  void forbiddenPreservesTheServiceCode() throws Exception {
    var response = new MockHttpServletResponse();
    denied
        .getValue()
        .handle(
            new MockHttpServletRequest("GET", "/resource"),
            response,
            new AccessDeniedException("denied"));
    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(json.readTree(response.getContentAsByteArray()).path("errorCode").asString())
        .isEqualTo("NO_ACCESS");
  }
}
