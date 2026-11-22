package com.sanigest.companyaccess;

import com.sanigest.companyaccess.CompanyAccess.MembershipRole;
import com.sanigest.companyaccess.CompanyAccess.Module;
import com.sanigest.companyaccess.CompanyAccess.Scope;
import com.sanigest.security.AccessTokens;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/** Versioned transport contract. */
public final class CompanyAccessProtocol {
  public static final String PATH = "/internal/v1/company-access";
  public static final String USER_ASSERTION = "X-Company-User-Assertion";
  public static final String AUDIENCE = "sanigest-company-access";
  public static final String TOKEN_TYPE = "sanigest-service+jwt";

  private CompanyAccessProtocol() {}

  public record Query(UUID userId, Module module, Scope scope, UUID target) {
    public static Query from(Jwt jwt) {
      var claims = jwt.getClaims();
      var tenant = claims.get("company_id");
      if (!(claims.get("module") instanceof String module)
          || !(claims.get("scope") instanceof String scope)
          || (claims.containsKey("company_id") && !(tenant instanceof String))) {
        throw new IllegalArgumentException("Invalid Company access claims");
      }
      return new Query(
          uuid(jwt.getSubject()),
          Module.valueOf(module),
          Scope.valueOf(scope),
          tenant == null ? null : uuid((String) tenant));
    }
  }

  public record Response(
      UUID userId,
      UUID companyId,
      Module module,
      Scope scope,
      String mode,
      MembershipRole role,
      Boolean explicitGrant) {}

  static UUID uuid(String value) {
    if (!AccessTokens.validSubject(value)) {
      throw new IllegalArgumentException("Invalid UUID");
    }
    return UUID.fromString(value);
  }
}
