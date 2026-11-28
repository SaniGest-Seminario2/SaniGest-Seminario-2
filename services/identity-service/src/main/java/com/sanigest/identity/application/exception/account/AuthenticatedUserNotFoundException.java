package com.sanigest.identity.application.exception.account;

public final class AuthenticatedUserNotFoundException extends RuntimeException {

  public AuthenticatedUserNotFoundException() {
    super("Authenticated user is unavailable");
  }
}
