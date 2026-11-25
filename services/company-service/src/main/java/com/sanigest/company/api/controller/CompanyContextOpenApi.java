package com.sanigest.company.api.controller;

import com.sanigest.company.api.routing.CompanyRoutes;
import com.sanigest.security.CompanySelector;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class CompanyContextOpenApi {
  private static final String COMPANIES = "/api/v1/companies";
  private static final Map<String, String> UUID = Map.of("type", "string", "format", "uuid");

  private CompanyContextOpenApi() {}

  static Map<String, Object> paths() {
    var paths = new LinkedHashMap<String, Object>();
    paths.put(COMPANIES, CompanyOpenApi.operations());
    paths.put(AdvisorCompaniesController.PATH, AdvisorAccessOpenApi.selectorOperations());
    paths.put(
        CompanyRoutes.ACCEPT_INVITATION,
        Map.of(
            "parameters",
                List.of(
                    Map.of(
                        "name",
                        "token",
                        "in",
                        "path",
                        "required",
                        true,
                        "schema",
                        Map.of("type", "string"))),
            "post", operation("Aceptar invitación por token", "200", false)));
    for (String base : List.of(COMPANIES + "/me", COMPANIES + "/{companyId}")) {
      boolean legacy = base.endsWith("/me");
      put(
          paths,
          base,
          legacy,
          Map.of(
              "get", operation("Consultar perfil empresarial", "200", legacy),
              "patch", operation("Actualizar perfil empresarial", "200", legacy)));
      put(
          paths,
          base + "/users",
          legacy,
          Map.of("get", operation("Listar miembros", "200", legacy)));
      put(
          paths,
          base + "/invitations",
          legacy,
          Map.of("post", operation("Invitar a la empresa", "201", legacy)));
      put(
          paths,
          base + "/members/{userId}/role",
          legacy,
          Map.of("patch", operation("Cambiar rol de un miembro", "200", legacy)));
      put(
          paths,
          base + "/members/{userId}",
          legacy,
          Map.of("delete", operation("Revocar una membresía", "204", legacy)));
      put(
          paths,
          base + "/audit-log",
          legacy,
          Map.of("get", operation("Consultar auditoría empresarial", "200", legacy)));
      put(paths, base + "/advisor-access", legacy, AdvisorAccessOpenApi.operations());
    }
    return paths;
  }

  private static void put(
      Map<String, Object> paths, String path, boolean legacy, Map<String, Object> operations) {
    var parameters = new java.util.ArrayList<Map<String, Object>>();
    parameters.add(
        Map.of(
            "name",
            CompanySelector.HEADER,
            "in",
            "header",
            "required",
            false,
            "schema",
            UUID,
            "description",
            legacy
                ? "Selector opcional. Sin selector se requiere una empresa inequívoca."
                : "Selector opcional. Si se envía, debe coincidir con companyId."));
    if (!legacy) {
      parameters.add(pathParameter("companyId"));
    }
    if (path.contains("{userId}")) {
      parameters.add(pathParameter("userId"));
    }
    var item = new LinkedHashMap<>(operations);
    item.put("parameters", parameters);
    paths.put(path, item);
  }

  private static Map<String, Object> pathParameter(String name) {
    return Map.of("name", name, "in", "path", "required", true, "schema", UUID);
  }

  private static Map<String, Object> operation(String summary, String success, boolean legacy) {
    var responses =
        new LinkedHashMap<>(
            Map.of(
                success,
                Map.of("description", "Operación completada."),
                "400",
                Map.of("description", "Petición o selector inválido o discordante."),
                "401",
                Map.of("description", "JWT inválido o ausente."),
                "403",
                Map.of("description", "Permiso empresarial insuficiente."),
                "404",
                Map.of("description", "Recurso ausente o invisible."),
                "503",
                Map.of("description", "Autorización indisponible.")));
    if (legacy) {
      responses.put(
          "409",
          Map.of("description", "COMPANY_SELECTION_REQUIRED si hay varias empresas activas."));
    }
    return Map.of(
        "summary",
        summary,
        "security",
        List.of(Map.of("bearerAuth", List.of())),
        "responses",
        responses);
  }
}
