package com.sanigest.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class OpenApiConfiguration {
  public static final String[] GET_PATHS = {
    "/v3/api-docs",
    "/v3/api-docs.yaml",
    "/v3/api-docs/swagger-config",
    "/swagger-ui.html",
    "/swagger-ui/**"
  };

  private static final Set<String> PUBLIC_AUTH =
      Set.of(
          "/api/v1/auth/register",
          "/api/v1/auth/login",
          "/api/v1/auth/refresh",
          "/api/v1/auth/logout");
  private static final Set<String> COMPANY_SCOPED =
      Set.of(
          "catalog-service",
          "sales-service",
          "inventory-service",
          "expense-service",
          "finance-service");

  @Bean
  OpenApiCustomizer sanigestContract(@Value("${spring.application.name}") String service) {
    return api -> describe(api, service);
  }

  private static void describe(OpenAPI api, String service) {
    api.setInfo(new Info().title("SaniGest " + service).version("0.1.0"));
    api.setServers(List.of(new Server().url("/")));
    if (api.getComponents() == null) {
      api.setComponents(new Components());
    }
    api.getComponents()
        .addSecuritySchemes(
            "bearerAuth",
            new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT"));
    api.addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    if ("api-gateway".equals(service)) {
      gatewayHealth(api);
    }
    if (api.getPaths() == null) {
      return;
    }
    api.getComponents().addSchemas("SanigestProblem", problemSchema());
    api.getPaths()
        .forEach((path, item) ->
            item.readOperationsMap()
                .forEach(
                    (method, operation) -> describeOperation(service, path, method, operation)));
  }

  private static void gatewayHealth(OpenAPI api) {
    if (api.getPaths() == null) {
      api.setPaths(new Paths());
    }
    for (String endpoint : List.of("health", "health/liveness", "health/readiness")) {
      api.path(
          "/actuator/" + endpoint,
          new PathItem()
              .get(
                  new Operation()
                      .operationId(endpoint.replace('/', '_'))
                      .addTagsItem("Gateway")
                      .summary(endpoint)
                      .security(List.of())
                      .responses(
                          new ApiResponses()
                              .addApiResponse("200", healthResponse("UP"))
                              .addApiResponse("503", healthResponse("DOWN")))));
    }
  }

  private static ApiResponse healthResponse(String status) {
    return new ApiResponse()
        .description(status)
        .content(
            new Content()
                .addMediaType(
                    "application/json",
                    new MediaType()
                        .schema(new ObjectSchema().addProperty("status", new StringSchema()))
                        .example(Map.of("status", status))));
  }

  private static void describeOperation(
      String service, String path, HttpMethod method, Operation operation) {
    if (PUBLIC_AUTH.contains(path)) {
      operation.setSecurity(List.of());
    } else if (!path.startsWith("/actuator/")) {
      problem(operation, HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED");
      problem(operation, HttpStatus.FORBIDDEN, "FORBIDDEN");
    }
    if (COMPANY_SCOPED.contains(service)) {
      operation.addParametersItem(
          new HeaderParameter()
              .name("X-Company-Id")
              .required(true)
              .description(
                  "UUID de la empresa seleccionada. El servicio verifica el acceso a esa empresa.")
              .schema(new StringSchema().format("uuid")));
      problem(operation, HttpStatus.BAD_REQUEST, "COMPANY_CONTEXT_REQUIRED");
    } else if (operation.getRequestBody() != null) {
      problem(operation, HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
    }
    if (Set.of("/api/v1/sales", "/api/v1/invoices").contains(path) && method == HttpMethod.POST) {
      problem(operation, HttpStatus.NOT_FOUND, "CATALOG_RESOURCE_NOT_FOUND");
      problem(operation, HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT");
    }
    operation.getResponses().values().stream()
        .filter(response -> response.get$ref() == null)
        .forEach(response ->
            response.addHeaderObject(
                "X-Correlation-Id",
                new Header()
                    .description("Identificador de la petición devuelto por el Gateway.")
                    .schema(new StringSchema().format("uuid"))));
  }

  private static Schema<?> problemSchema() {
    return new ObjectSchema()
        .addProperty("type", new StringSchema().format("uri"))
        .addProperty("title", new StringSchema())
        .addProperty("status", new IntegerSchema())
        .addProperty("detail", new StringSchema())
        .addProperty("instance", new StringSchema().format("uri-reference"))
        .addProperty("errorCode", new StringSchema())
        .addProperty("timestamp", new StringSchema().format("date-time"))
        .addProperty("traceId", new StringSchema().pattern("^[a-fA-F0-9]{32}$"))
        .addProperty("errors", new ObjectSchema());
  }

  private static void problem(Operation operation, HttpStatus status, String code) {
    String type = HttpProblems.type(code).toString();
    String detail =
        switch (code) {
          case "COMPANY_CONTEXT_REQUIRED" -> "Provide a valid company selection";
          case "CATALOG_RESOURCE_NOT_FOUND" -> "Catalog resource not found";
          case "IDEMPOTENCY_CONFLICT" ->
              "This Idempotency-Key was used with a different sale request";
          default -> HttpProblems.detail(status);
        };
    operation
        .getResponses()
        .putIfAbsent(
            Integer.toString(status.value()),
            new ApiResponse()
                .description(status.getReasonPhrase())
                .content(
                    new Content()
                        .addMediaType(
                            "application/problem+json",
                            new MediaType()
                                .schema(new Schema<>().$ref("#/components/schemas/SanigestProblem"))
                                .example(
                                    Map.of(
                                        "type",
                                        type,
                                        "title",
                                        status.getReasonPhrase(),
                                        "status",
                                        status.value(),
                                        "detail",
                                        detail,
                                        "errorCode",
                                        code,
                                        "timestamp",
                                        "2026-01-01T00:00:00Z",
                                        "traceId",
                                        "0123456789abcdef0123456789abcdef")))));
  }
}
