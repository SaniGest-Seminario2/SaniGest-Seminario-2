package com.sanigest.catalog.api.error;

import static com.sanigest.catalog.api.error.CatalogProblems.problem;

import com.sanigest.catalog.application.exception.CatalogAuthorizationUnavailable;
import com.sanigest.catalog.application.exception.InvalidClientQueryException;
import com.sanigest.catalog.domain.exception.InvalidClientPatch;
import com.sanigest.catalog.domain.exception.InvalidSupplierDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
class CatalogRequestErrorHandler {
  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    InvalidClientQueryException.class,
    InvalidClientPatch.class,
    InvalidSupplierDetails.class
  })
  ProblemDetail validation() {
    return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The request is invalid");
  }

  @ExceptionHandler(CatalogAuthorizationUnavailable.class)
  ProblemDetail authorizationUnavailable() {
    return problem(
        HttpStatus.SERVICE_UNAVAILABLE,
        "AUTHORIZATION_UNAVAILABLE",
        "Company authorization could not be verified");
  }
}
