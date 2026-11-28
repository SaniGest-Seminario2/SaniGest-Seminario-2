package com.sanigest.sales.application.exception.security;

public final class SalesAccessForbidden extends RuntimeException {
  public SalesAccessForbidden() {
    super("Sales access denied");
  }
}
