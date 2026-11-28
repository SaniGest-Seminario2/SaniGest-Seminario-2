package com.sanigest.sales.application.exception.invoice;

public final class InvoiceNotFound extends RuntimeException {
  public InvoiceNotFound() {
    super("Invoice not found");
  }
}
