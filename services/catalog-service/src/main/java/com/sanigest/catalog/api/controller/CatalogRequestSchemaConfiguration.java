package com.sanigest.catalog.api.controller;

import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.OpenAPI30To31;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.oas.models.Paths;
import java.io.IOException;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
class CatalogRequestSchemaConfiguration {
  private static final List<String> REQUEST_SCHEMAS =
      List.of(
          "CreateClientRequest",
          "CreateExpandedClientRequest",
          "UpdateClientRequest",
          "ChangeClientStatusRequest",
          "CreateSupplierRequest",
          "SupplierPatchRequest",
          "SupplierStatusRequest",
          "SupplierStatus",
          "ReplaceSupplierItemsRequest",
          "SupplierItemOfferRequest");

  @Bean
  OpenApiCustomizer catalogRequestBodies() throws IOException {
    OpenAPI published;
    try (var input = new ClassPathResource("catalog-openapi.json").getInputStream()) {
      published = Json.mapper().readValue(input, OpenAPI.class);
    }
    new OpenAPI30To31().process(published);
    var sources = published.getPaths();
    return api -> {
      REQUEST_SCHEMAS.forEach(name ->
          api.getComponents()
              .getSchemas()
              .putIfAbsent(name, published.getComponents().getSchemas().get(name)));
      copyBody(api, sources, "/api/v1/clients", HttpMethod.POST);
      copyBody(api, sources, "/api/v1/clients/{id}", HttpMethod.PATCH);
      copyBody(api, sources, "/api/v1/clients/{id}/status", HttpMethod.PATCH);
      copyBody(api, sources, "/api/v1/suppliers", HttpMethod.POST);
      copyBody(api, sources, "/api/v1/suppliers/{id}", HttpMethod.PATCH);
      copyBody(api, sources, "/api/v1/suppliers/{id}/status", HttpMethod.PATCH);
      copyBody(api, sources, "/api/v1/suppliers/{id}/items", HttpMethod.PUT);
    };
  }

  private static void copyBody(OpenAPI api, Paths sources, String path, HttpMethod method) {
    var body = sources.get(path).readOperationsMap().get(method).getRequestBody();
    api.getPaths().get(path).readOperationsMap().get(method).setRequestBody(body);
  }
}
