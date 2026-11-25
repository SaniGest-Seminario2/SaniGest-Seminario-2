package com.sanigest.catalog.application.exception;

public final class SupplierCommercialException extends RuntimeException {
  public enum Reason {
    DUPLICATE_SUPPLIER_CATEGORY,
    SUPPLIER_CATEGORY_NOT_FOUND,
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
