package com.sanigest.sales.application.exception;

public final class SaleAmountNotRepresentable extends RuntimeException {
  public SaleAmountNotRepresentable(IllegalArgumentException cause) {
    super(cause);
  }
}
