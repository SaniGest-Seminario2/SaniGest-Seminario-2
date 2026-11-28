package com.sanigest.company.api.security;

import com.sanigest.company.application.usecase.access.RequireRegularAccount;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

public final class CompanyActor {
  private CompanyActor() {}

  public static UUID authenticatedUserId(Jwt jwt, RequireRegularAccount regularAccount) {
    UUID userId;
    try {
      userId = UUID.fromString(jwt.getSubject());
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("Invalid authenticated identity");
    }
    regularAccount.check(jwt.getTokenValue());
    return userId;
  }
}
