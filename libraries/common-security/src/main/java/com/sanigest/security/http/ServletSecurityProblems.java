package com.sanigest.security.http;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public final class ServletSecurityProblems {
  private final ObjectMapper json;
  private final String forbiddenCode;

  public ServletSecurityProblems(JsonMapper json, String forbiddenCode) {
    this.json =
        json.rebuild()
            .addMixIn(org.springframework.http.ProblemDetail.class, ProblemDetailJacksonMixin.class)
            .build();
    this.forbiddenCode = forbiddenCode;
  }

  public void configure(HttpSecurity http) {
    http.exceptionHandling(exceptions ->
        exceptions.authenticationEntryPoint(unauthorized()).accessDeniedHandler(forbidden()));
  }

  public void configure(OAuth2ResourceServerConfigurer<HttpSecurity> resource) {
    resource.authenticationEntryPoint(unauthorized()).accessDeniedHandler(forbidden());
  }

  private AuthenticationEntryPoint unauthorized() {
    var challenge = new BearerTokenAuthenticationEntryPoint();
    return (request, response, failure) -> {
      challenge.commence(request, response, failure);
      // JWT decoder messages can contain request data. Keep the Bearer challenge and error token.
      String header = response.getHeader(HttpHeaders.WWW_AUTHENTICATE);
      if (header != null && header.contains(", error_description=")) {
        response.setHeader(
            HttpHeaders.WWW_AUTHENTICATE,
            header.substring(0, header.indexOf(", error_description=")));
      }
      var status = HttpStatus.valueOf(response.getStatus());
      write(request, response, status, HttpProblems.code(status));
    };
  }

  private AccessDeniedHandler forbidden() {
    var challenge = new BearerTokenAccessDeniedHandler();
    return (request, response, failure) -> {
      challenge.handle(request, response, failure);
      write(request, response, HttpStatus.FORBIDDEN, forbiddenCode);
    };
  }

  private void write(
      HttpServletRequest request, HttpServletResponse response, HttpStatus status, String code)
      throws IOException {
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    if (!"HEAD".equals(request.getMethod())) {
      json.writeValue(
          response.getOutputStream(),
          HttpProblems.create(
              status, code, HttpProblems.detail(status), request.getHeader("X-Trace-Id")));
    }
  }
}
