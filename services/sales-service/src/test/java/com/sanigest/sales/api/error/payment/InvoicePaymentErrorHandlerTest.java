package com.sanigest.sales.api.error.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.sanigest.sales.application.exception.payment.InvoicePaymentConflict;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

class InvoicePaymentErrorHandlerTest {
  private final InvoicePaymentErrorHandler handler = new InvoicePaymentErrorHandler();

  @Test
  void mapsPaymentFailuresToStableProblemDetails() {
    assertProblem(handler.notFound(), HttpStatus.NOT_FOUND, "INVOICE_NOT_FOUND");
    assertProblem(handler.invalidKey(), HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY");
    assertProblem(handler.idempotencyConflict(), HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT");
    assertProblem(
        handler.admissionUnavailable(),
        HttpStatus.SERVICE_UNAVAILABLE,
        "PAYMENT_ADMISSION_UNAVAILABLE");

    var problem = handler.conflict(new InvoicePaymentConflict("Invoice is inactive"));
    assertProblem(problem, HttpStatus.CONFLICT, "INVOICE_PAYMENT_CONFLICT");
    assertThat(problem.getDetail()).isEqualTo("Invoice is inactive");
  }

  private static void assertProblem(ProblemDetail problem, HttpStatus status, String code) {
    assertThat(problem.getStatus()).isEqualTo(status.value());
    assertThat(problem.getProperties()).containsEntry("errorCode", code);
  }
}
