package com.sanigest.catalog.application;

public final class ClientAccessException extends RuntimeException {
  public enum Reason {
    FORBIDDEN,
    CLIENT_NOT_FOUND,
    CLIENT_UPDATE_CONFLICT
  }

  private final Reason reason;

  public ClientAccessException(Reason reason) {
    super(reason.name());
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
