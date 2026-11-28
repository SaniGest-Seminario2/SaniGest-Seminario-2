package com.sanigest.sales.api.error.invoice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

class InvoiceErrorHandlerTest {
  private final InvoiceErrorHandler handler = new InvoiceErrorHandler();

  @Test
  void exposesStableProblemCodesAndStatuses() {
    assertProblem(handler.invalidRequest(), 400, "INVALID_REQUEST");
    assertProblem(handler.invalidKey(), 400, "INVALID_IDEMPOTENCY_KEY");
    assertProblem(handler.saleNotFound(), 404, "SALE_NOT_FOUND");
    assertProblem(handler.invoiceConflict(), 409, "INVOICE_CONFLICT");
    assertProblem(handler.idempotencyConflict(), 409, "IDEMPOTENCY_CONFLICT");
    assertProblem(handler.companyUnavailable(), 503, "INVOICE_COMPANY_UNAVAILABLE");
  }

  private static void assertProblem(ProblemDetail problem, int status, String code) {
    assertThat(problem.getStatus()).isEqualTo(status);
    assertThat(problem.getProperties()).containsEntry("errorCode", code);
  }
}
