package com.sanigest.sales.application.exception;

public final class InvoicePaymentConflict extends RuntimeException {
  public InvoicePaymentConflict(String message) {
    super(message);
  }
}
