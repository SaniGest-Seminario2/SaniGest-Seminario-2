package com.sanigest.catalog.application.exception.supplier;

public final class SupplierAccessException extends RuntimeException {
  public enum Reason {
    FORBIDDEN,
    SUPPLIER_NOT_FOUND,
    DUPLICATE_TAX_ID,
    SUPPLIER_UPDATE_CONFLICT
  }

  private final Reason reason;

  public SupplierAccessException(Reason reason) {
    super(reason.name());
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
