package com.sanigest.expense.application.exception;

public final class ExpenseFailure extends RuntimeException {
  public enum Kind {
    INVALID,
    NOT_FOUND,
    CONFLICT,
    FORBIDDEN,
    UNAVAILABLE
  }

  private final Kind kind;

  public ExpenseFailure(Kind kind, String code) {
    super(code);
    this.kind = kind;
  }

  public Kind kind() {
    return kind;
  }

  public static ExpenseFailure invalid(String code) {
    return new ExpenseFailure(Kind.INVALID, code);
  }
}
