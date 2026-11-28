package com.sanigest.finance.application.exception.reporting;

public class FinancialSourceFailure extends RuntimeException {
  public enum Kind {
    UNAUTHENTICATED,
    FORBIDDEN,
    UNAVAILABLE
  }

  private final Kind kind;

  public FinancialSourceFailure(Kind kind, String message) {
    super(message);
    this.kind = kind;
  }

  public Kind kind() {
    return kind;
  }
}
