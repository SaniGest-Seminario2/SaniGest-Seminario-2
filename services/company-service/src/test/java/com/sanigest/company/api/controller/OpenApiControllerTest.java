package com.sanigest.company.api.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

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

  @Test
  void publishesCompanyRegistrationListingAndAdvisorPaths() {
    var paths = new ObjectMapper().valueToTree(controller.apiDocs()).path("paths");
    var companies = paths.path("/api/v1/companies");
    assertThat(companies.path("get").path("responses").has("200")).isTrue();
    assertThat(companies.path("post").path("responses").has("201")).isTrue();
    assertThat(companies.path("post").path("parameters").get(0).path("name").stringValue())
        .isEqualTo("Idempotency-Key");
    assertThat(paths.has(AdvisorAccessController.PATH)).isTrue();
    assertThat(paths.has(AdvisorCompaniesController.PATH)).isTrue();
  }

  @Test
  void describesTheOptInPagePendingInvitationsAndMembershipStatus() {
    var document = new ObjectMapper().valueToTree(controller.apiDocs());
    var paths = document.path("paths");
    String company = "/api/v1/companies/{companyId}";
    var list = paths.path(company + "/users").path("get");
    assertThat(list.path("description").asString()).contains("view=page", "OWNER", "ADMIN");
    assertThat(
            list.path("responses")
                .path("200")
                .path("content")
                .path("application/json")
                .path("schema")
                .path("oneOf")
                .size())
        .isEqualTo(2);
    assertThat(
            paths
                .path(company + "/invitations")
                .path("post")
                .path("requestBody")
                .path("content")
                .path("application/json")
                .path("schema")
                .path("properties")
                .has("name"))
        .isTrue();
    assertThat(paths.path(company + "/invitations/{invitationId}").has("delete")).isTrue();
    assertThat(paths.path(company + "/members/{userId}/status").has("patch")).isTrue();
  }
}
