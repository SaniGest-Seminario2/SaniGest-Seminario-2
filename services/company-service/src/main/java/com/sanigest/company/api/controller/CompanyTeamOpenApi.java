package com.sanigest.company.api.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class CompanyTeamOpenApi {
  private static final Map<String, Object> UUID = Map.of("type", "string", "format", "uuid");
  private static final Map<String, Object> EMAIL =
      Map.of("type", "string", "format", "email", "maxLength", 320);
  private static final Map<String, Object> NAME = Map.of("type", "string", "maxLength", 160);
  private static final Map<String, Object> DATE = Map.of("type", "string", "format", "date-time");
  private static final Map<String, Object> ROLES = choices("OWNER", "ADMIN", "OPERATOR", "VIEWER");
  private static final Map<String, Object> STATES = choices("ACTIVE", "SUSPENDED", "REVOKED");
  private static final Map<String, Object> PAGE =
      Map.of("type", "integer", "minimum", 0, "default", 0);
  private static final Map<String, Object> SIZE =
      Map.of("type", "integer", "minimum", 1, "maximum", 100, "default", 20);
  private static final Map<String, Object> INVITABLE_ROLES = choices("OPERATOR", "VIEWER");
  private static final Map<String, Object> INVITATION =
      Map.of(
          "invitationId",
          UUID,
          "role",
          INVITABLE_ROLES,
          "status",
          choices("PENDING"),
          "expiresAt",
          DATE,
          "email",
          EMAIL,
          "name",
          Map.of("type", "string", "maxLength", 160, "nullable", true));

  private CompanyTeamOpenApi() {}

  static Map<String, Object> schemas() {
    var member =
        Map.<String, Object>of(
            "membershipId", UUID, "userId", UUID, "role", ROLES, "status", STATES);
    var summary = new LinkedHashMap<>(member);
    summary.put("name", Map.of("type", "string", "maxLength", 160, "nullable", true));
    summary.put("email", Map.of("type", "string", "format", "email", "nullable", true));
    var pending = new LinkedHashMap<>(INVITATION);
    pending.put("name", Map.of("type", "string", "maxLength", 160, "nullable", true));
    pending.put("email", EMAIL);
    pending.put("createdAt", DATE);
    return Map.of(
        "CompanyMember", object(member, List.copyOf(member.keySet())),
        "CompanyMemberSummary", object(summary, List.copyOf(summary.keySet())),
        "CompanyMemberPage",
            object(
                Map.of(
                    "content",
                    array(ref("CompanyMemberSummary")),
                    "page",
                    PAGE,
                    "size",
                    SIZE,
                    "totalElements",
                    Map.of("type", "integer", "format", "int64"),
                    "totalPages",
                    Map.of("type", "integer", "format", "int64")),
                List.of("content", "page", "size", "totalElements", "totalPages")),
        "PendingCompanyInvitation",
            object(
                pending,
                List.of("invitationId", "email", "role", "status", "createdAt", "expiresAt")),
        "CompanyInvitationCreated", object(INVITATION, List.copyOf(INVITATION.keySet())),
        "CompanyProfile",
            object(
                Map.of(
                    "companyId",
                    UUID,
                    "companyName",
                    NAME,
                    "businessName",
                    Map.of("type", "string", "nullable", true),
                    "rnc",
                    Map.of("type", "string", "nullable", true),
                    "location",
                    Map.of("type", "string", "nullable", true),
                    "currency",
                    choices("DOP"),
                    "status",
                    choices("ACTIVE"),
                    "email",
                    Map.of("type", "string", "format", "email", "nullable", true),
                    "phone",
                    Map.of("type", "string", "nullable", true)),
                List.of("companyId", "companyName", "currency", "status")));
  }

  static Map<String, Object> members(Map<String, Object> operation) {
    var result =
        response(
            operation,
            "200",
            Map.of("oneOf", List.of(array(ref("CompanyMember")), ref("CompanyMemberPage"))));
    result.put(
        "description",
        "Sin view conserva el array histórico. view=page habilita la página para OWNER o ADMIN de la empresa. "
            + "La búsqueda por nombre o email ignora mayúsculas y distingue acentos.");
    result.put(
        "parameters",
        List.of(
            query("view", choices("page")),
            query("search", NAME),
            query("status", STATES),
            query("role", ROLES),
            query("page", PAGE),
            query("size", SIZE),
            query("sort", choices("membershipId,asc", "membershipId,desc"))));
    return result;
  }

  static Map<String, Object> invite(Map<String, Object> operation) {
    var result = response(operation, "201", ref("CompanyInvitationCreated"));
    result.put(
        "description",
        "Crea una invitación con TTL configurable (PT72H). replace=true invalida el token anterior.");
    result.put(
        "requestBody",
        body(
            object(
                Map.of(
                    "name",
                    NAME,
                    "email",
                    EMAIL,
                    "role",
                    INVITABLE_ROLES,
                    "replace",
                    Map.of("type", "boolean", "default", false)),
                List.of("email", "role"))));
    return result;
  }

  static Map<String, Object> pending(Map<String, Object> operation) {
    var result = response(operation, "200", array(ref("PendingCompanyInvitation")));
    result.put("parameters", List.of(query("page", PAGE), query("size", SIZE)));
    return result;
  }

  static Map<String, Object> status(Map<String, Object> operation) {
    var result = response(operation, "200", ref("CompanyMember"));
    result.put(
        "requestBody",
        body(object(Map.of("status", choices("ACTIVE", "SUSPENDED")), List.of("status"))));
    return result;
  }

  static Map<String, Object> accepted(Map<String, Object> operation) {
    var result = response(operation, "200", ref("CompanyMember"));
    result.put(
        "description",
        "Un solo uso. La empresa procede de la invitación. "
            + "La aceptación en otra empresa requiere que el modo multiempresa esté habilitado.");
    return result;
  }

  static Map<String, Object> response(
      Map<String, Object> operation, String code, Map<String, Object> schema) {
    var result = new LinkedHashMap<>(operation);
    var responses = new LinkedHashMap<Object, Object>();
    responses.putAll((Map<?, ?>) operation.get("responses"));
    responses.put(code, Map.of("description", "Operación completada.", "content", content(schema)));
    result.put("responses", responses);
    return result;
  }

  private static Map<String, Object> body(Map<String, Object> schema) {
    return Map.of("required", true, "content", content(schema));
  }

  private static Map<String, Object> content(Map<String, Object> schema) {
    return Map.of("application/json", Map.of("schema", schema));
  }

  private static Map<String, Object> object(Map<String, Object> fields, List<String> required) {
    return Map.of("type", "object", "properties", fields, "required", required);
  }

  private static Map<String, Object> query(String name, Map<String, Object> schema) {
    return Map.of("in", "query", "name", name, "schema", schema);
  }

  private static Map<String, Object> choices(String... choices) {
    return Map.of("type", "string", "enum", List.of(choices));
  }

  private static Map<String, Object> array(Map<String, Object> item) {
    return Map.of("type", "array", "items", item);
  }

  private static Map<String, Object> ref(String name) {
    return Map.of("$ref", "#/components/schemas/" + name);
  }
}
