package com.sanigest.sales.api.error;

import static com.sanigest.sales.api.error.SalesProblems.problem;

import com.sanigest.sales.api.controller.SaleController;
import com.sanigest.sales.application.exception.CatalogAccessDeniedException;
import com.sanigest.sales.application.exception.CatalogResourceInvalidException;
import com.sanigest.sales.application.exception.CatalogUnavailableException;
import com.sanigest.sales.application.exception.InvalidSaleRequest;
import com.sanigest.sales.application.exception.SaleAmountNotRepresentable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = SaleController.class)
class SaleErrorHandler {
  @ExceptionHandler({
    InvalidSaleRequest.class,
    MethodArgumentNotValidException.class,
    HttpMessageNotReadableException.class
  })
  ProblemDetail invalidRequest() {
    return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The sale request is invalid");
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
}
