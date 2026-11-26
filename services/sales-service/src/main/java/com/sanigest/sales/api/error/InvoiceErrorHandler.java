package com.sanigest.sales.api.error;

import static com.sanigest.sales.api.error.SalesProblems.problem;

import com.sanigest.sales.api.controller.InvalidIdempotencyKey;
import com.sanigest.sales.api.controller.InvoiceController;
import com.sanigest.sales.application.exception.IdempotencyConflict;
import com.sanigest.sales.application.exception.InvalidSaleRequest;
import com.sanigest.sales.application.exception.InvoiceCompanyUnavailable;
import com.sanigest.sales.application.exception.InvoiceConflict;
import com.sanigest.sales.application.exception.SaleNotFound;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(0)
@RestControllerAdvice(assignableTypes = InvoiceController.class)
class InvoiceErrorHandler {
  @ExceptionHandler(InvalidSaleRequest.class)
  ProblemDetail invalidRequest() {
    return problem(
        HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Provide a sale UUID and an empty body");
  }

  @ExceptionHandler(InvalidIdempotencyKey.class)
  ProblemDetail invalidKey() {
    return problem(
        HttpStatus.BAD_REQUEST,
        "INVALID_IDEMPOTENCY_KEY",
        "Provide one Idempotency-Key with 1 to 128 characters from A-Z, a-z, 0-9, . _ ~ -");
  }

  @ExceptionHandler(SaleNotFound.class)
  ProblemDetail saleNotFound() {
    return problem(HttpStatus.NOT_FOUND, "SALE_NOT_FOUND", "Sale not found");
  }

  @ExceptionHandler(InvoiceConflict.class)
  ProblemDetail invoiceConflict() {
    return problem(
        HttpStatus.CONFLICT, "INVOICE_CONFLICT", "The sale is not eligible for invoicing");
  }

  @ExceptionHandler(IdempotencyConflict.class)
  ProblemDetail idempotencyConflict() {
    return problem(
        HttpStatus.CONFLICT,
        "IDEMPOTENCY_CONFLICT",
        "This Idempotency-Key was used with a different invoice request");
  }

  @ExceptionHandler(InvoiceCompanyUnavailable.class)
  ProblemDetail companyUnavailable() {
    return problem(
        HttpStatus.SERVICE_UNAVAILABLE,
        "INVOICE_COMPANY_UNAVAILABLE",
        "Company snapshot could not be verified");
  }
}
