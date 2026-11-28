package com.sanigest.catalog.application.exception.client;

public final class ClientCreationException extends RuntimeException {
  public enum Reason {
    FORBIDDEN,
    AUTHORIZATION_UNAVAILABLE,
    DUPLICATE_TAX_ID
  }

  private final Reason reason;

  public ClientCreationException(Reason reason) {
    super(reason.name());
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
