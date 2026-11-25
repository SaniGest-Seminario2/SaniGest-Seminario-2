package com.sanigest.catalog.api.error;

import static com.sanigest.catalog.api.error.CatalogProblems.problem;

import com.sanigest.catalog.application.exception.ClientAccessException;
import com.sanigest.catalog.application.exception.ClientCreationException;
import com.sanigest.catalog.application.exception.ClientListingForbiddenException;
import com.sanigest.catalog.application.exception.DuplicateClientCategory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ClientErrorHandler {
  @ExceptionHandler(DuplicateClientCategory.class)
  ProblemDetail duplicateCategory() {
    return problem(
        HttpStatus.CONFLICT,
        "DUPLICATE_CLIENT_CATEGORY",
        "A category with that code already exists");
  }

  @ExceptionHandler(ClientListingForbiddenException.class)
  ProblemDetail listingForbidden(ClientListingForbiddenException failure) {
    return problem(HttpStatus.FORBIDDEN, "FORBIDDEN", failure.getMessage());
  }

  @ExceptionHandler(ClientAccessException.class)
  ProblemDetail clientAccess(ClientAccessException failure) {
    String code = failure.reason().name();
    return switch (failure.reason()) {
      case FORBIDDEN -> problem(HttpStatus.FORBIDDEN, code, "Client access is not authorized");
      case CLIENT_NOT_FOUND -> problem(HttpStatus.NOT_FOUND, code, "Client not found");
      case CLIENT_UPDATE_CONFLICT ->
          problem(HttpStatus.CONFLICT, code, "The client was changed by another request");
    };
  }

  @ExceptionHandler(ClientCreationException.class)
  ProblemDetail creation(ClientCreationException failure) {
    String errorCode = failure.reason().name();
    return switch (failure.reason()) {
      case FORBIDDEN ->
          problem(HttpStatus.FORBIDDEN, errorCode, "Client creation is not authorized");
      case AUTHORIZATION_UNAVAILABLE ->
          problem(
              HttpStatus.SERVICE_UNAVAILABLE,
              errorCode,
              "Company authorization could not be verified");
      case DUPLICATE_TAX_ID ->
          problem(
              HttpStatus.CONFLICT,
              errorCode,
              "A client with that tax ID already exists in the company");
    };
  }
}
