package com.sanigest.company.api.controller;

import java.util.List;
import java.util.Map;

final class CompanyOpenApi {
  private static final Map<String, String> TEXT = Map.of("type", "string");
  private static final Map<String, String> UUID = Map.of("type", "string", "format", "uuid");
  private static final List<Map<String, List<String>>> SECURITY =
      List.of(Map.of("bearerAuth", List.of()));
  private static final Map<String, Object> EXAMPLE =
      Map.of(
          "companyName",
          "Distribuidora La Económica SRL",
          "businessName",
          "Distribuidora La Económica",
          "rnc",
          "123456789",
          "email",
          "administracion@example.com",
          "phone",
          "8095550100",
          "location",
          "Santo Domingo Este");
  private static final Map<String, Object> SUMMARY =
      Map.of(
          "companyId",
          "e3fc1fd0-42ea-4a0d-9c14-6cbd44592b42",
          "companyName",
          EXAMPLE.get("companyName"),
          "businessName",
          EXAMPLE.get("businessName"),
          "role",
          "OWNER",
          "membershipStatus",
          "ACTIVE",
          "status",
          "ACTIVE");

  private CompanyOpenApi() {}

  static Map<String, Object> operations() {
    return Map.of("get", listing(), "post", registration());
  }

  private static Map<String, Object> listing() {
    var properties =
        Map.of(
            "companyId",
            UUID,
            "companyName",
            TEXT,
            "businessName",
            TEXT,
            "role",
            values("OWNER", "ADMIN", "OPERATOR", "VIEWER"),
            "membershipStatus",
            values("ACTIVE"),
            "status",
            values("ACTIVE"));
    return Map.of(
        "summary",
        "List companies for the authenticated REGULAR account",
        "description",
        "Returns active companies with active memberships, ordered by legal name and UUID. "
            + "An account without authorized companies receives []. "
            + "No company selector is required.",
        "security",
        SECURITY,
        "responses",
        Map.of(
            "200",
                response(
                    "Authorized companies",
                    Map.of(
                        "type",
                        "array",
                        "items",
                        object(
                            properties,
                            "companyId",
                            "companyName",
                            "businessName",
                            "role",
                            "membershipStatus",
                            "status")),
                    List.of(SUMMARY)),
            "401", error("Missing or invalid JWT"),
            "403", error("Account is inactive or is not REGULAR"),
            "503", error("Current account authorization is unavailable")));
  }

  private static Map<String, Object> registration() {
    var request =
        object(
            Map.of(
                "companyName", Map.of("type", "string", "maxLength", 160),
                "businessName", Map.of("type", "string", "maxLength", 160),
                "rnc",
                    Map.of("type", "string", "nullable", true, "pattern", "^([0-9]{9}|[0-9]{11})$"),
                "email", Map.of("type", "string", "format", "email", "maxLength", 320),
                "phone", Map.of("type", "string", "pattern", "^[0-9+() .-]{7,32}$"),
                "location", Map.of("type", "string", "maxLength", 255),
                "ownerId",
                    Map.of(
                        "type",
                        "string",
                        "description",
                        "Legacy field used only in the idempotency hash."),
                "userId",
                    Map.of(
                        "type",
                        "string",
                        "description",
                        "Legacy field used only in the idempotency hash.")),
            "companyName",
            "businessName",
            "email",
            "phone",
            "location");
    var result =
        object(
            Map.of(
                "companyId",
                UUID,
                "companyName",
                TEXT,
                "businessName",
                TEXT,
                "rnc",
                Map.of("type", "string", "nullable", true),
                "email",
                TEXT,
                "phone",
                TEXT,
                "location",
                TEXT,
                "currency",
                values("DOP"),
                "status",
                values("ACTIVE")),
            "companyId",
            "companyName",
            "businessName",
            "email",
            "phone",
            "location",
            "currency",
            "status");
    var example = new java.util.LinkedHashMap<>(EXAMPLE);
    example.put("companyId", SUMMARY.get("companyId"));
    example.put("currency", "DOP");
    example.put("status", "ACTIVE");
    return Map.of(
        "summary",
        "Register a company with an active OWNER membership",
        "description",
        "The authenticated REGULAR account becomes OWNER. "
            + "Multi-company creation requires activation. "
            + "Legacy /me routes return COMPANY_SELECTION_REQUIRED (409) "
            + "when several active companies are eligible.",
        "security",
        SECURITY,
        "parameters",
        List.of(
            Map.of(
                "name",
                "Idempotency-Key",
                "in",
                "header",
                "required",
                true,
                "schema",
                Map.of("type", "string", "minLength", 1, "maxLength", 128),
                "description",
                "Scoped to the authenticated user. "
                    + "An identical replay returns the original company while access remains active.")),
        "requestBody",
        Map.of("required", true, "content", json(request, EXAMPLE)),
        "responses",
        Map.of(
            "201", response("Registered company", result, example),
            "400", error("Invalid payload or idempotency key"),
            "401", error("Missing or invalid JWT"),
            "403", error("Account is unauthorized or access to the original company was lost"),
            "409",
                error(
                    "Duplicate RNC, different payload for a reused key, or compatibility-mode restriction"),
            "503", error("Current account authorization is unavailable")));
  }

  private static Map<String, Object> object(Map<String, ?> properties, String... required) {
    return Map.of("type", "object", "properties", properties, "required", List.of(required));
  }

  private static Map<String, Object> values(String... values) {
    return Map.of("type", "string", "enum", List.of(values));
  }

  private static Map<String, Object> response(
      String description, Map<String, Object> schema, Object example) {
    return Map.of("description", description, "content", json(schema, example));
  }

  private static Map<String, Object> json(Map<String, Object> schema, Object example) {
    return Map.of("application/json", Map.of("schema", schema, "example", example));
  }

  private static Map<String, String> error(String description) {
    return Map.of("description", description);
  }
}
