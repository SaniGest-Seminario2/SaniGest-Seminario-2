package com.sanigest.catalog.application.exception.supplier;

public final class SupplierCommercialException extends RuntimeException {
  public enum Reason {
    INVALID_SUPPLIER_CATEGORY,
    INCOMPATIBLE_SUPPLIER_ITEM,
    PRIMARY_SUPPLIER_CONFLICT
  }

  private final Reason reason;

  public SupplierCommercialException(Reason reason) {
    super(reason.name());
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
