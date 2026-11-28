package com.sanigest.sales.api.error.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SalesAuthorizationErrorHandlerTest {
  private final SalesAuthorizationErrorHandler errors = new SalesAuthorizationErrorHandler();

  @Test
  void distinguishesDenialsFromDependencyOutages() {
    var forbidden = errors.forbidden();
    assertThat(forbidden.getStatus()).isEqualTo(403);
    assertThat(forbidden.getProperties()).containsEntry("errorCode", "FORBIDDEN");

    var unavailable = errors.unavailable();
    assertThat(unavailable.getStatus()).isEqualTo(503);
    assertThat(unavailable.getProperties()).containsEntry("errorCode", "AUTHORIZATION_UNAVAILABLE");
  }
}
