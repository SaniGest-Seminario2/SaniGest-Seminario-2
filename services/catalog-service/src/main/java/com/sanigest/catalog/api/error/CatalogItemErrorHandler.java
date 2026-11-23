package com.sanigest.catalog.api.error;

import static com.sanigest.catalog.api.error.CatalogProblems.problem;

import com.sanigest.catalog.application.exception.CatalogItemCreationForbidden;
import com.sanigest.catalog.domain.exception.InvalidCatalogItem;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class CatalogItemErrorHandler {
  @ExceptionHandler(InvalidCatalogItem.class)
  ProblemDetail invalidItem() {
    return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The catalog item is invalid");
  }

  @ExceptionHandler(CatalogItemCreationForbidden.class)
  ProblemDetail forbidden() {
    return problem(HttpStatus.FORBIDDEN, "FORBIDDEN", "Catalog item creation is not authorized");
  }
}
