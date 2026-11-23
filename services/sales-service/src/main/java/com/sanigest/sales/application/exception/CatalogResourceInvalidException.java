package com.sanigest.sales.application.exception;

public final class CatalogResourceInvalidException extends RuntimeException {
  public enum Reason {
    INVALID_REQUEST,
    NOT_FOUND_OR_NOT_VISIBLE,
    INVALID_RESPONSE,
    NOT_ELIGIBLE,
    INACTIVE
  }

  private final Reason reason;

  public CatalogResourceInvalidException(Reason reason) {
    super("Catalog: " + reason);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
