package com.sanigest.sales.application.exception.invoice;

public final class InvoiceStockConflict extends RuntimeException {
  public InvoiceStockConflict(String code) {
    super(code);
  }
}
