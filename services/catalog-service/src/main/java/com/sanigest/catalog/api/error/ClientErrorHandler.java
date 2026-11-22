package com.sanigest.catalog.api.error;

import com.sanigest.catalog.application.exception.ClientAccessException;
import com.sanigest.catalog.application.exception.ClientCreationException;
import com.sanigest.catalog.application.exception.ClientListingForbiddenException;
import com.sanigest.catalog.application.exception.InvalidClientQueryException;
import com.sanigest.catalog.domain.exception.InvalidClientPatch;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
class ClientErrorHandler {
  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    InvalidClientQueryException.class,
    InvalidClientPatch.class
  })
  ProblemDetail validation() {
    return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The request is invalid");
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

  private static ProblemDetail problem(HttpStatus status, String code, String detail) {
    var problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setProperty("errorCode", code);
    problem.setProperty("timestamp", Instant.now());
    return problem;
  }
}
