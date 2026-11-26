package com.sanigest.inventory.infrastructure.security;

import com.sanigest.inventory.observability.InventoryRequestTrace;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.ObjectMapper;

final class SecurityProblems {
  private final ObjectMapper json;

  SecurityProblems(ObjectMapper json) {
    this.json = json;
  }

  AuthenticationEntryPoint unauthorized() {
    var challenge = new BearerTokenAuthenticationEntryPoint();
    return (request, response, failure) -> {
      challenge.commence(request, response, failure);
      write(response, HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED");
    };
  }

  AccessDeniedHandler forbidden() {
    return (request, response, failure) ->
        write(response, HttpStatus.FORBIDDEN, "INVENTORY_ACCESS_DENIED");
  }

  private void write(HttpServletResponse response, HttpStatus status, String code)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    json.writeValue(
        response.getOutputStream(),
        Map.of(
            "type", "about:blank",
            "title", status.getReasonPhrase(),
            "status", status.value(),
            "errorCode", code,
            "timestamp", Instant.now(),
            "traceId", InventoryRequestTrace.current()));
  }
}
