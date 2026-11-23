package com.sanigest.sales.application.exception;

public final class SalesAccessForbidden extends RuntimeException {
  public SalesAccessForbidden() {
    super("Sales access denied");
  }
}
