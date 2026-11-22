package com.sanigest.identity.application.exception;

public final class AuthenticatedUserNotFoundException extends RuntimeException {

  public AuthenticatedUserNotFoundException() {
    super("Authenticated user is unavailable");
  }
}
