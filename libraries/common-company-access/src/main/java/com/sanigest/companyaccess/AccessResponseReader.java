package com.sanigest.companyaccess;

import com.sanigest.companyaccess.CompanyAccess.MembershipRole;
import com.sanigest.companyaccess.CompanyAccessProtocol.AccessMode;
import com.sanigest.companyaccess.CompanyAccessProtocol.Query;
import com.sanigest.companyaccess.CompanyAccessProtocol.Response;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;

final class AccessResponseReader {
  private static final Set<String> FIELDS =
      Set.of(
          "userId", "companyId", "module", "scope", "mode", "role", "explicitGrant", "companyName");

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
      if (json == null || !json.isObject() || !FIELDS.containsAll(json.propertyNames())) {
        throw new CompanyAccessUnavailable();
      }
      var user = CompanyAccessProtocol.uuid(requiredText(json, "userId"));
      var company = CompanyAccessProtocol.uuid(requiredText(json, "companyId"));
      var mode = AccessMode.valueOf(requiredText(json, "mode"));
      if (!query.userId().equals(user)
          || !query.companyId().equals(company)
          || query.mode() != mode
          || !query.module().name().equals(requiredText(json, "module"))
          || !query.scope().name().equals(requiredText(json, "scope"))) {
        throw new CompanyAccessUnavailable();
      }
      if (mode == AccessMode.DELEGATED) {
        validateDelegation(json);
        return new Response(user, company, query.module(), query.scope(), mode, null, null, null);
      }
      if (!json.path("explicitGrant").isBoolean()) {
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

  private static void validateDelegation(JsonNode json) {
    if (json.hasNonNull("role") || json.hasNonNull("explicitGrant") || json.has("companyName")) {
      throw new CompanyAccessUnavailable();
    }
  }

  private static String companyName(JsonNode json, Query query) {
    var name = json.path("companyName");
    if (query.module() == CompanyAccess.Module.SALES
        && query.scope() == CompanyAccess.Scope.WRITE) {
      if (!name.isString() || name.stringValue().isBlank()) {
        throw new CompanyAccessUnavailable();
      }
      return name.stringValue();
    }
    if (json.has("companyName")) {
      throw new CompanyAccessUnavailable();
    }
    return null;
  }

  private static String requiredText(JsonNode json, String name) {
    var value = json.path(name);
    if (!value.isString()) {
      throw new CompanyAccessUnavailable();
    }
    return value.stringValue();
  }
}
