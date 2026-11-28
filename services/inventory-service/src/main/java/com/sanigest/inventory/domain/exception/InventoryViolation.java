package com.sanigest.inventory.domain.exception;

public final class InventoryViolation extends RuntimeException {
  private final Kind kind;

  public enum Kind {
    INVALID,
    MISSING,
    CONFLICT,
    FORBIDDEN,
    UNAVAILABLE
  }

  public InventoryViolation(Kind kind, String code) {
    super(code);
    this.kind = kind;
  }

  public Kind kind() {
    return kind;
  }

  public static InventoryViolation invalid(String code) {
    return new InventoryViolation(Kind.INVALID, code);
  }

  public static InventoryViolation conflict(String code) {
    return new InventoryViolation(Kind.CONFLICT, code);
  }
}
