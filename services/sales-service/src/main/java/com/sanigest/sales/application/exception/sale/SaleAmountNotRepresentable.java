package com.sanigest.sales.application.exception.sale;

public final class SaleAmountNotRepresentable extends RuntimeException {
  public SaleAmountNotRepresentable(IllegalArgumentException cause) {
    super(cause);
  }
}
