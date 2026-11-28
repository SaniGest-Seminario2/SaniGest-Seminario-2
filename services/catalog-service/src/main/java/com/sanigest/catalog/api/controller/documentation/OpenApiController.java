package com.sanigest.catalog.api.controller.documentation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(
    name = "springdoc.api-docs.enabled",
    havingValue = "false",
    matchIfMissing = true)
class OpenApiController {
  @GetMapping(value = "/v3/api-docs", produces = MediaType.APPLICATION_JSON_VALUE)
  Resource apiDocs() {
    return new ClassPathResource("catalog-openapi.json");
  }
}
