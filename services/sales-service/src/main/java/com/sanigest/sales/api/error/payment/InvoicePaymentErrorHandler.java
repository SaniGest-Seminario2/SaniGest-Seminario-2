package com.sanigest.sales.api.error.payment;

import static com.sanigest.sales.api.error.SalesProblems.problem;

import com.sanigest.sales.api.controller.payment.ActiveShiftPaymentController;
import com.sanigest.sales.api.controller.payment.InvoicePaymentController;
import com.sanigest.sales.api.error.InvalidIdempotencyKey;
import com.sanigest.sales.application.exception.idempotency.IdempotencyConflict;
import com.sanigest.sales.application.exception.invoice.InvoiceNotFound;
import com.sanigest.sales.application.exception.payment.InvoicePaymentConflict;
import com.sanigest.sales.application.exception.payment.PaymentAdmissionUnavailable;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(0)
@RestControllerAdvice(
    assignableTypes = {InvoicePaymentController.class, ActiveShiftPaymentController.class})
class InvoicePaymentErrorHandler {
  @ExceptionHandler(InvoiceNotFound.class)
  ProblemDetail notFound() {
    return problem(HttpStatus.NOT_FOUND, "INVOICE_NOT_FOUND", "Invoice not found");
  }

  @ExceptionHandler(InvoicePaymentConflict.class)
  ProblemDetail conflict(InvoicePaymentConflict error) {
    return problem(HttpStatus.CONFLICT, "INVOICE_PAYMENT_CONFLICT", error.getMessage());
  }

  @ExceptionHandler(InvalidIdempotencyKey.class)
  ProblemDetail invalidKey() {
    return problem(
        HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY", "Provide one valid Idempotency-Key");
  }

  @ExceptionHandler(IdempotencyConflict.class)
  ProblemDetail idempotencyConflict() {
    return problem(
        HttpStatus.CONFLICT,
        "IDEMPOTENCY_CONFLICT",
        "Key was used with a different payment request");
  }

  @ExceptionHandler(PaymentAdmissionUnavailable.class)
  ProblemDetail admissionUnavailable() {
    return problem(
        HttpStatus.SERVICE_UNAVAILABLE,
        "PAYMENT_ADMISSION_UNAVAILABLE",
        "Payment admission could not be verified");
  }
}
