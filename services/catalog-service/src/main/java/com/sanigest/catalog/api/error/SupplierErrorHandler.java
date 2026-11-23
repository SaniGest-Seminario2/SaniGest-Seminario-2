package com.sanigest.catalog.api.error;

import com.sanigest.catalog.application.exception.SupplierCreationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class SupplierErrorHandler {
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
