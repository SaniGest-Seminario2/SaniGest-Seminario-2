package com.sanigest.catalog.api.error;

import com.sanigest.catalog.application.exception.CatalogAuthorizationUnavailable;
import com.sanigest.catalog.application.exception.SupplierCreationException;
import com.sanigest.catalog.domain.exception.InvalidSupplierDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class SupplierErrorHandler {
  @ExceptionHandler(InvalidSupplierDetails.class)
  ProblemDetail invalidDetails() {
    return CatalogProblems.problem(
        HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The request is invalid");
  }

  @ExceptionHandler(CatalogAuthorizationUnavailable.class)
  ProblemDetail authorizationUnavailable() {
    return CatalogProblems.problem(
        HttpStatus.SERVICE_UNAVAILABLE,
        "AUTHORIZATION_UNAVAILABLE",
        "Company authorization could not be verified");
  }

  @ExceptionHandler(SupplierCreationException.class)
  ProblemDetail creation(SupplierCreationException failure) {
    var code = failure.reason().name();
    return switch (failure.reason()) {
      case FORBIDDEN ->
          CatalogProblems.problem(
              HttpStatus.FORBIDDEN, code, "Supplier creation is not authorized");
      case DUPLICATE_TAX_ID ->
          CatalogProblems.problem(
              HttpStatus.CONFLICT,
              code,
              "A supplier with that tax ID already exists in the company");
    };
  }
}
