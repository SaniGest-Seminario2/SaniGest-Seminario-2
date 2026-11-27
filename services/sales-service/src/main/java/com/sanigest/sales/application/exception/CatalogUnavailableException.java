package com.sanigest.sales.application.exception;

public final class CatalogUnavailableException extends RuntimeException {
  public enum Reason {
    NOT_CONFIGURED,
    RATE_LIMITED,
    CONFLICT,
    DEPENDENCY_FAILURE,
    TRANSPORT_FAILURE
  }

  private final Reason reason;

  public CatalogUnavailableException(Reason reason) {
    super("Catalog: " + reason);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
