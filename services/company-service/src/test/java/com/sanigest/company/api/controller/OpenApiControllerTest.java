package com.sanigest.company.api.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OpenApiControllerTest {
  private final OpenApiController controller = new OpenApiController();

  @Test
  void publishesBasicOpenApiDescription() {
    assertThat(controller.apiDocs())
        .containsEntry("openapi", "3.0.3")
        .containsEntry(
            "info", java.util.Map.of("title", "SaniGest Company Service", "version", "0.1.0"))
        .containsKey("paths");
    assertThat(controller.swaggerUi().getUrl()).isEqualTo("/v3/api-docs");
  }
}
