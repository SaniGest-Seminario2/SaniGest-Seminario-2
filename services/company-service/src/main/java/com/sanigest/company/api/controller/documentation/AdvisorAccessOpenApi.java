package com.sanigest.company.api.controller.documentation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AdvisorAccessOpenApi {
  private static final String JSON = "application/json";
  private static final Map<String, String> UUID = Map.of("type", "string", "format", "uuid");
  private static final Map<String, Object> REVISION =
      Map.of("type", "integer", "format", "int64", "minimum", 0);
  private static final Map<String, Object> PERMISSION =
      Map.of(
          "type", "object",
          "required", List.of("module"),
          "properties",
              Map.of(
                  "module",
                      Map.of(
                          "type",
                          "string",
                          "enum",
                          List.of("CATALOG", "SALES", "EXPENSE", "FINANCE")),
                  "scope",
                      Map.of(
                          "type", "string", "enum", List.of("READ", "WRITE"), "default", "READ")));
  private static final Map<String, Object> ACCESS =
      Map.of(
          "type", "object",
          "required", List.of("advisorId", "revision", "permissions"),
          "properties",
              Map.of(
                  "advisorId",
                  UUID,
                  "revision",
                  REVISION,
                  "permissions",
                  Map.of("type", "array", "items", PERMISSION)));

  private AdvisorAccessOpenApi() {}

  static Map<String, Object> operations() {
    return Map.of(
        "get",
            operation(
                "List advisor access and current revisions for the owner's company",
                "200",
                Map.of("type", "array", "items", ACCESS),
                Map.of(),
                false),
        "put",
            operation(
                "Replace advisor permissions at the supplied revision (zero for an initial grant)",
                "200",
                ACCESS,
                Map.of(
                    "requestBody",
                    Map.of(
                        "required",
                        true,
                        "content",
                        content(
                            Map.of(
                                "type",
                                "object",
                                "required",
                                List.of("advisorId", "permissions"),
                                "properties",
                                Map.of(
                                    "advisorId",
                                    UUID,
                                    "revision",
                                    REVISION,
                                    "permissions",
                                    Map.of(
                                        "type",
                                        "array",
                                        "items",
                                        PERMISSION,
                                        "minItems",
                                        1,
                                        "maxItems",
                                        8,
                                        "uniqueItems",
                                        true)))))),
                true),
        "delete",
            operation(
                "Revoke all access for an advisor in the owner's company",
                "204",
                null,
                Map.of(
                    "parameters",
                    List.of(
                        Map.of(
                            "name", "advisorId", "in", "query", "required", true, "schema", UUID))),
                false));
  }

  static Map<String, Object> selectorOperations() {
    var permission =
        Map.of(
            "type", "object",
            "required", List.of("module", "scope"),
            "properties",
                Map.of(
                    "module",
                        Map.of(
                            "type",
                            "string",
                            "enum",
                            List.of("CATALOG", "SALES", "EXPENSE", "FINANCE")),
                    "scope", Map.of("type", "string", "enum", List.of("READ", "WRITE"))));
    var company =
        Map.of(
            "type", "object",
            "required", List.of("companyId", "companyName", "permissions"),
            "properties",
                Map.of(
                    "companyId", UUID,
                    "companyName", Map.of("type", "string"),
                    "permissions",
                        Map.of(
                            "type",
                            "array",
                            "items",
                            permission,
                            "minItems",
                            1,
                            "uniqueItems",
                            true)));
    return Map.of(
        "get",
        Map.of(
            "summary",
                "List active companies with current permissions for the authenticated advisor",
            "security", List.of(Map.of("bearerAuth", List.of())),
            "responses",
                Map.of(
                    "200",
                        Map.of(
                            "description",
                                "Authorized companies ordered by name and id, or an empty array",
                            "headers",
                                Map.of(
                                    "Cache-Control",
                                    Map.of(
                                        "schema",
                                        Map.of("type", "string", "enum", List.of("no-store")))),
                            "content", content(Map.of("type", "array", "items", company))),
                    "401", Map.of("description", "Missing or invalid user JWT"),
                    "403", Map.of("description", "Account is not eligible for advisor access"),
                    "503", Map.of("description", "Identity authorization could not be verified"))));
  }

  private static Map<String, Object> operation(
      String summary,
      String status,
      Object schema,
      Map<String, Object> details,
      boolean acceptsRevision) {
    var responses = new LinkedHashMap<String, Object>();
    responses.put(
        status,
        schema == null
            ? Map.of("description", "Access revoked")
            : Map.of("description", "Current advisor access", "content", content(schema)));
    if (!details.isEmpty()) {
      responses.put("400", Map.of("description", "Invalid request"));
    }
    responses.put("401", Map.of("description", "Missing or invalid user JWT"));
    responses.put("403", Map.of("description", "Actor or recipient is unauthorized"));
    responses.put("404", Map.of("description", "Actor has no company"));
    if (acceptsRevision) {
      responses.put("409", Map.of("description", "Advisor access revision has changed"));
    }
    responses.put("503", Map.of("description", "Identity authorization could not be verified"));
    var operation = new LinkedHashMap<>(details);
    operation.put("summary", summary);
    operation.put("security", List.of(Map.of("bearerAuth", List.of())));
    operation.put("responses", responses);
    return operation;
  }

  private static Map<String, Object> content(Object schema) {
    return Map.of(JSON, Map.of("schema", schema));
  }
}
