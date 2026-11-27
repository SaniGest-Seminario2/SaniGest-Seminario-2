package com.sanigest.sales.api.error;

import static com.sanigest.sales.api.error.SalesProblems.problem;

import com.sanigest.sales.api.controller.InvoiceDocumentsController;
import com.sanigest.sales.application.exception.InvalidSaleQuery;
import com.sanigest.sales.application.exception.InvalidSaleRequest;
import com.sanigest.sales.application.exception.InvoiceConflict;
import com.sanigest.sales.application.exception.InvoiceListLimitExceeded;
import com.sanigest.sales.application.exception.InvoiceNotFound;
import com.sanigest.sales.application.exception.SaleCancellationConflict;
import com.sanigest.sales.application.usecase.invoice.ManageInvoices;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(0)
@RestControllerAdvice(assignableTypes = InvoiceDocumentsController.class)
class InvoiceDocumentsErrorHandler {
  @ExceptionHandler({InvalidSaleQuery.class, InvalidSaleRequest.class})
  ProblemDetail invalidRequest() {
    return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid invoice request");
  }

  @ExceptionHandler(InvoiceListLimitExceeded.class)
  ProblemDetail listLimitExceeded() {
    return problem(
        HttpStatus.UNPROCESSABLE_CONTENT,
        "INVOICE_LIST_LIMIT_EXCEEDED",
        "More than "
            + ManageInvoices.MAX_UNPAGED_RESULTS
            + " invoices match. Refine the search or use pagination.");
  }

  @ExceptionHandler(InvoiceNotFound.class)
  ProblemDetail notFound() {
    return problem(HttpStatus.NOT_FOUND, "INVOICE_NOT_FOUND", "Invoice not found");
  }

  @ExceptionHandler({InvoiceConflict.class, SaleCancellationConflict.class})
  ProblemDetail conflict() {
    return problem(HttpStatus.CONFLICT, "INVOICE_CONFLICT", "Invoice cannot be cancelled");
  }
}
