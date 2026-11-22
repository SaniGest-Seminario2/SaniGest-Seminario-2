package com.sanigest.company.api.controller;

import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

/** Dependency-free OpenAPI contract. */
@RestController
public class OpenApiController {
  @GetMapping(value = "/v3/api-docs", produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, Object> apiDocs() {
    return Map.of(
        "openapi", "3.0.3",
        "info", Map.of("title", "SaniGest Company Service", "version", "0.1.0"),
        "paths", Map.of(AdvisorAccessController.PATH, AdvisorAccessOpenApi.operations()),
        "components",
            Map.of(
                "securitySchemes",
                Map.of(
                    "bearerAuth",
                    Map.of("type", "http", "scheme", "bearer", "bearerFormat", "JWT"))));
  }

  @GetMapping("/swagger-ui.html")
  public RedirectView swaggerUi() {
    return new RedirectView("/v3/api-docs");
  }
}
