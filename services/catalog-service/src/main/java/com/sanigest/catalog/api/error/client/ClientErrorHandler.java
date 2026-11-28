package com.sanigest.catalog.api.error.client;

import static com.sanigest.web.api.error.HttpProblems.create;

import com.sanigest.catalog.application.exception.client.ClientAccessException;
import com.sanigest.catalog.application.exception.client.ClientCreationException;
import com.sanigest.catalog.application.exception.client.ClientFinancialsUnavailable;
import com.sanigest.catalog.application.exception.client.ClientListingForbiddenException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(0)
@RestControllerAdvice
class ClientErrorHandler {
  @ExceptionHandler(ClientFinancialsUnavailable.class)
  ProblemDetail financialsUnavailable() {
    return create(
        HttpStatus.SERVICE_UNAVAILABLE,
        "CLIENT_FINANCIALS_UNAVAILABLE",
        "Client financial data could not be retrieved");
  }

  @ExceptionHandler(ClientListingForbiddenException.class)
  ProblemDetail listingForbidden(ClientListingForbiddenException failure) {
    return create(HttpStatus.FORBIDDEN, "FORBIDDEN", failure.getMessage());
  }

  @ExceptionHandler(ClientAccessException.class)
  ProblemDetail clientAccess(ClientAccessException failure) {
    String code = failure.reason().name();
    return switch (failure.reason()) {
      case FORBIDDEN -> create(HttpStatus.FORBIDDEN, code, "Client access is not authorized");
      case CLIENT_NOT_FOUND -> create(HttpStatus.NOT_FOUND, code, "Client not found");
      case CLIENT_UPDATE_CONFLICT ->
          create(HttpStatus.CONFLICT, code, "The client was changed by another request");
    };
  }

  @ExceptionHandler(ClientCreationException.class)
  ProblemDetail creation(ClientCreationException failure) {
    String errorCode = failure.reason().name();
    return switch (failure.reason()) {
      case FORBIDDEN ->
          create(HttpStatus.FORBIDDEN, errorCode, "Client creation is not authorized");
      case AUTHORIZATION_UNAVAILABLE ->
          create(
              HttpStatus.SERVICE_UNAVAILABLE,
              errorCode,
              "Company authorization could not be verified");
      case DUPLICATE_TAX_ID ->
          create(
              HttpStatus.CONFLICT,
              errorCode,
              "A client with that tax ID already exists in the company");
    };
  }
}
