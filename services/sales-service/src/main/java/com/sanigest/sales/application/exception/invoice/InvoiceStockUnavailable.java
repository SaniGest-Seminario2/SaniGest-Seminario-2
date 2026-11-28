package com.sanigest.sales.application.exception.invoice;

public final class InvoiceStockUnavailable extends RuntimeException {
  public InvoiceStockUnavailable() {}

  public InvoiceStockUnavailable(Throwable cause) {
    super(cause);
  }
}
