package com.sanigest.companyaccess;

import com.sanigest.companyaccess.CompanyAccess.MembershipRole;
import com.sanigest.companyaccess.CompanyAccessProtocol.Query;
import com.sanigest.companyaccess.CompanyAccessProtocol.Response;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;

final class AccessResponseReader {
  private final ObjectReader reader;

  AccessResponseReader(ObjectMapper mapper) {
    reader =
        mapper
            .reader()
            .with(
                DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
  }

  Response read(byte[] body, Query query) {
    try {
      var json = reader.readTree(body);
      if (json == null || !json.isObject()) {
        throw new CompanyAccessUnavailable();
      }
      var user = CompanyAccessProtocol.uuid(requiredText(json, "userId"));
      var company = CompanyAccessProtocol.uuid(requiredText(json, "companyId"));
      if (!query.userId().equals(user)
          || !query.module().name().equals(requiredText(json, "module"))
          || !query.scope().name().equals(requiredText(json, "scope"))) {
        throw new CompanyAccessUnavailable();
      }
      var mode = requiredText(json, "mode");
      if (query.target() != null) {
        validateDelegation(json, query, company, mode);
        return new Response(user, company, query.module(), query.scope(), mode, null, null, null);
      }
      if (!"MEMBERSHIP".equals(mode) || !json.path("explicitGrant").isBoolean()) {
        throw new CompanyAccessUnavailable();
      }
      return new Response(
          user,
          company,
          query.module(),
          query.scope(),
          mode,
          MembershipRole.valueOf(requiredText(json, "role")),
          json.path("explicitGrant").booleanValue(),
          companyName(json, query));
    } catch (JacksonException | IllegalArgumentException invalid) {
      throw new CompanyAccessUnavailable();
    }
  }

  private static String companyName(JsonNode json, Query query) {
    var name = json.path("companyName");
    if (query.module() == CompanyAccess.Module.SALES
        && query.scope() == CompanyAccess.Scope.WRITE
        && name.isString()) {
      return name.stringValue();
    }
    return null;
  }

  private static void validateDelegation(
      JsonNode json, Query query, java.util.UUID company, String mode) {
    if (!query.target().equals(company)
        || !"DELEGATED".equals(mode)
        || json.hasNonNull("role")
        || json.hasNonNull("explicitGrant")) {
      throw new CompanyAccessUnavailable();
    }
  }

  private static String requiredText(JsonNode json, String name) {
    var value = json.path(name);
    if (!value.isString()) {
      throw new CompanyAccessUnavailable();
    }
    return value.stringValue();
  }
}
