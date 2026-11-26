package com.sanigest.catalog.application.exception;

public final class InventoryLookupFailed extends RuntimeException {
  public enum Reason {
    FORBIDDEN,
    WAREHOUSE_NOT_FOUND,
    UNAVAILABLE
  }

  private final Reason reason;

  public InventoryLookupFailed(Reason reason) {
    super(reason.name());
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
