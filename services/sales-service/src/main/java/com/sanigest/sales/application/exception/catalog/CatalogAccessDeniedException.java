package com.sanigest.sales.application.exception.catalog;

public final class CatalogAccessDeniedException extends RuntimeException {
  public enum Reason {
    MISSING_IDENTITY,
    AUTHENTICATION_REJECTED,
    FORBIDDEN
  }

  private final Reason reason;

  public CatalogAccessDeniedException(Reason reason) {
    super("Catalog: " + reason);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
