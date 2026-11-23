package com.sanigest.catalog.api.error;

import static com.sanigest.catalog.api.error.CatalogProblems.problem;

import com.sanigest.catalog.application.exception.CatalogItemAccessException;
import com.sanigest.catalog.application.exception.CatalogItemCreationForbidden;
import com.sanigest.catalog.application.exception.InvalidCatalogItemQuery;
import com.sanigest.catalog.domain.exception.InvalidCatalogItem;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class CatalogItemErrorHandler {
  @ExceptionHandler({InvalidCatalogItem.class, InvalidCatalogItemQuery.class})
  ProblemDetail invalidItem() {
    return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The catalog item is invalid");
  }

  @ExceptionHandler(CatalogItemCreationForbidden.class)
  ProblemDetail forbidden() {
    return problem(HttpStatus.FORBIDDEN, "FORBIDDEN", "Catalog item creation is not authorized");
  }

  @ExceptionHandler(CatalogItemAccessException.class)
  ProblemDetail access(CatalogItemAccessException failure) {
    var code = failure.reason().name();
    return switch (failure.reason()) {
      case FORBIDDEN ->
          problem(HttpStatus.FORBIDDEN, code, "Catalog item access is not authorized");
      case CATALOG_ITEM_NOT_FOUND -> problem(HttpStatus.NOT_FOUND, code, "Catalog item not found");
      case CATALOG_ITEM_UPDATE_CONFLICT ->
          problem(HttpStatus.CONFLICT, code, "The catalog item was changed by another request");
    };
  }
}
