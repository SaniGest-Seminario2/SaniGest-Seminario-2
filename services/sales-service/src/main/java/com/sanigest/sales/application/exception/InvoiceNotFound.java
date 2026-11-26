package com.sanigest.sales.application.exception;

public final class InvoiceNotFound extends RuntimeException {
  public InvoiceNotFound() {
    super("Invoice not found");
  }
}
