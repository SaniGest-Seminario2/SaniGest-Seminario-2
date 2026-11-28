package com.sanigest.catalog.application.exception.supplier;

public class SupplierCreationException extends RuntimeException {
  public enum Reason {
    FORBIDDEN,
    DUPLICATE_TAX_ID
  }

  private final Reason reason;

  public SupplierCreationException(Reason reason) {
    super(reason.name());
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
