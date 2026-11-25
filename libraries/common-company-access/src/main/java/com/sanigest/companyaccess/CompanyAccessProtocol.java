package com.sanigest.companyaccess;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sanigest.companyaccess.CompanyAccess.MembershipRole;
import com.sanigest.companyaccess.CompanyAccess.Module;
import com.sanigest.companyaccess.CompanyAccess.Scope;
import com.sanigest.security.AccessTokens;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/** Versioned transport contract. */
public final class CompanyAccessProtocol {
  public static final String PATH = "/internal/v2/company-access";
  public static final String USER_ASSERTION = "X-Company-User-Assertion";
  public static final String AUDIENCE = "sanigest-company-access-v2";
  public static final String TOKEN_TYPE = "sanigest-service+jwt";

  private CompanyAccessProtocol() {}

  public enum AccessMode {
    MEMBERSHIP,
    DELEGATED
  }

  public record Query(UUID userId, Module module, Scope scope, UUID companyId, AccessMode mode) {
    public Query {
      java.util.Objects.requireNonNull(userId);
      java.util.Objects.requireNonNull(module);
      java.util.Objects.requireNonNull(scope);
      java.util.Objects.requireNonNull(companyId);
      java.util.Objects.requireNonNull(mode);
    }

    public static Query from(Jwt jwt) {
      var claims = jwt.getClaims();
      var tenant = claims.get("company_id");
      if (!(claims.get("module") instanceof String module)
          || !(claims.get("scope") instanceof String scope)
          || !(tenant instanceof String companyId)
          || !(claims.get("mode") instanceof String mode)) {
        throw new IllegalArgumentException("Invalid Company access claims");
      }
      return new Query(
          uuid(jwt.getSubject()),
          Module.valueOf(module),
          Scope.valueOf(scope),
          uuid(companyId),
          AccessMode.valueOf(mode));
    }
  }

  public record Response(
      UUID userId,
      UUID companyId,
      Module module,
      Scope scope,
      AccessMode mode,
      MembershipRole role,
      Boolean explicitGrant,
      @JsonInclude(JsonInclude.Include.NON_NULL) String companyName) {}

  static UUID uuid(String value) {
    if (!AccessTokens.validSubject(value)) {
      throw new IllegalArgumentException("Invalid UUID");
    }
    return UUID.fromString(value);
  }
}
