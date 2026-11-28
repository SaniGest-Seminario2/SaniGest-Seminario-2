package com.sanigest.sales.api.error.sale;

import static com.sanigest.sales.api.error.SalesProblems.problem;

import com.sanigest.sales.api.controller.invoice.CreateInvoiceController;
import com.sanigest.sales.api.controller.sale.SaleController;
import com.sanigest.sales.api.error.InvalidIdempotencyKey;
import com.sanigest.sales.application.exception.catalog.CatalogAccessDeniedException;
import com.sanigest.sales.application.exception.catalog.CatalogResourceInvalidException;
import com.sanigest.sales.application.exception.catalog.CatalogUnavailableException;
import com.sanigest.sales.application.exception.idempotency.IdempotencyConflict;
import com.sanigest.sales.application.exception.idempotency.IdempotencyInProgress;
import com.sanigest.sales.application.exception.invoice.InvoiceCompanyUnavailable;
import com.sanigest.sales.application.exception.invoice.InvoiceStockUnavailable;
import com.sanigest.sales.application.exception.sale.InvalidSaleRequest;
import com.sanigest.sales.application.exception.sale.SaleAmountNotRepresentable;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(0)
@RestControllerAdvice(assignableTypes = {SaleController.class, CreateInvoiceController.class})
class SaleErrorHandler {
  @ExceptionHandler(InvalidSaleRequest.class)
  ProblemDetail invalidRequest() {
    return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The sale request is invalid");
  }

  @ExceptionHandler(InvoiceCompanyUnavailable.class)
  ProblemDetail companyUnavailable() {
    return problem(
        HttpStatus.SERVICE_UNAVAILABLE,
        "INVOICE_COMPANY_UNAVAILABLE",
        "Company snapshot could not be verified");
  }

  @ExceptionHandler(InvoiceStockUnavailable.class)
  ProblemDetail stockUnavailable() {
    return problem(
        HttpStatus.SERVICE_UNAVAILABLE,
        "INVOICE_STOCK_UNAVAILABLE",
        "Inventory tracking could not be verified");
  }

  @ExceptionHandler(CatalogResourceInvalidException.class)
  ProblemDetail catalogResource(CatalogResourceInvalidException failure) {
    return switch (failure.reason()) {
      case NOT_FOUND_OR_NOT_VISIBLE ->
          problem(HttpStatus.NOT_FOUND, "CATALOG_RESOURCE_NOT_FOUND", "Catalog resource not found");
      case INACTIVE, NOT_ELIGIBLE ->
          problem(
              HttpStatus.UNPROCESSABLE_CONTENT,
              "CATALOG_RESOURCE_NOT_ELIGIBLE",
              "A Catalog resource is not eligible for this sale");
      case INVALID_REQUEST, INVALID_RESPONSE ->
          problem(
              HttpStatus.SERVICE_UNAVAILABLE,
              "CATALOG_CONTRACT_INVALID",
              "Catalog resource could not be verified");
    };
  }

  @ExceptionHandler(CatalogAccessDeniedException.class)
  ProblemDetail catalogForbidden() {
    return problem(HttpStatus.FORBIDDEN, "FORBIDDEN", "Catalog access denied");
  }

  @ExceptionHandler(CatalogUnavailableException.class)
  ProblemDetail catalogUnavailable() {
    return problem(HttpStatus.SERVICE_UNAVAILABLE, "CATALOG_UNAVAILABLE", "Catalog is unavailable");
  }

  @ExceptionHandler(SaleAmountNotRepresentable.class)
  ProblemDetail amounts() {
    return problem(
        HttpStatus.UNPROCESSABLE_CONTENT,
        "SALE_AMOUNT_NOT_REPRESENTABLE",
        "Sale amounts must fit the supported range and be exact to two decimals");
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ProblemDetail conflict() {
    return problem(HttpStatus.CONFLICT, "SALE_CONFLICT", "The sale could not be recorded");
  }

  @ExceptionHandler(InvalidIdempotencyKey.class)
  ProblemDetail invalidKey() {
    return problem(
        HttpStatus.BAD_REQUEST,
        "INVALID_IDEMPOTENCY_KEY",
        "Provide one Idempotency-Key with 1 to 128 characters from A-Z, a-z, 0-9, . _ ~ -");
  }

  @ExceptionHandler(IdempotencyConflict.class)
  ProblemDetail idempotencyConflict() {
    return problem(
        HttpStatus.CONFLICT,
        "IDEMPOTENCY_CONFLICT",
        "This Idempotency-Key was used with a different sale request");
  }

  @ExceptionHandler(IdempotencyInProgress.class)
  ResponseEntity<ProblemDetail> idempotencyInProgress() {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .header("Retry-After", "1")
        .body(
            problem(
                HttpStatus.CONFLICT,
                "IDEMPOTENCY_IN_PROGRESS",
                "The sale request is being processed. Retry with the same key"));
  }
}
