package com.sanigest.identity.application.exception;

public final class DuplicateUserEmailException extends RuntimeException {

  public DuplicateUserEmailException(Throwable cause) {
    super("A user already exists for this email", cause);
  }
}
