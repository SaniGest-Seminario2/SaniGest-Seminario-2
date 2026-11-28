package com.sanigest.company.application.exception.profile;

public class CompanyConflictException extends RuntimeException {
  public CompanyConflictException(String message, Throwable cause) {
    super(message, cause);
  }

  public CompanyConflictException(String message) {
    super(message);
  }
}
