package com.sanigest.identity.application.exception;

public final class DuplicateEmailException extends RuntimeException {

  public DuplicateEmailException() {
    super("An account already exists for this email");
  }
}
