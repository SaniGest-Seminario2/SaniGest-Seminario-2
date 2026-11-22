package com.sanigest.company.api.controller;

import com.sanigest.company.application.usecase.access.RequireRegularAccount;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

final class CompanyActor {
  private CompanyActor() {}

  static UUID authenticatedUserId(Jwt jwt, RequireRegularAccount regularAccount) {
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
