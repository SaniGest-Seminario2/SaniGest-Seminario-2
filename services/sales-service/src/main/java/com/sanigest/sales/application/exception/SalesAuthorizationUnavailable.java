package com.sanigest.sales.application.exception;

public final class SalesAuthorizationUnavailable extends RuntimeException {
  public SalesAuthorizationUnavailable() {
    super("Sales authorization unavailable");
  }
}
