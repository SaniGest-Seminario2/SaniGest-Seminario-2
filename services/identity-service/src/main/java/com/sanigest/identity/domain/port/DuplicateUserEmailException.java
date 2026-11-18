package com.sanigest.identity.domain.port;

public final class DuplicateUserEmailException extends RuntimeException {

  public DuplicateUserEmailException(Throwable cause) {
    super("A user already exists for this email", cause);
  }
}
