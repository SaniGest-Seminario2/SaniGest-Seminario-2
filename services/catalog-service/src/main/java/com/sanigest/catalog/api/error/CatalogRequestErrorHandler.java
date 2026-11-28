package com.sanigest.catalog.api.error;

import static com.sanigest.web.api.error.HttpProblems.create;

import com.sanigest.catalog.application.exception.client.InvalidClientQueryException;
import com.sanigest.catalog.application.exception.security.CatalogAuthorizationUnavailable;
import com.sanigest.catalog.domain.exception.client.InvalidClientPatch;
import com.sanigest.catalog.domain.exception.supplier.InvalidSupplierDetails;
import com.sanigest.security.api.request.company.CompanySelector.InvalidContext;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(0)
@RestControllerAdvice
class CatalogRequestErrorHandler {
  @ExceptionHandler(InvalidContext.class)
  ProblemDetail invalidCompanyContext(InvalidContext failure) {
    return create(HttpStatus.BAD_REQUEST, failure.getMessage(), "Invalid company selection");
  }

  @ExceptionHandler({
    InvalidClientQueryException.class,
    InvalidClientPatch.class,
    InvalidSupplierDetails.class
  })
  ProblemDetail validation() {
    return create(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The request is invalid");
  }

  @ExceptionHandler(CatalogAuthorizationUnavailable.class)
  ProblemDetail authorizationUnavailable() {
    return create(
        HttpStatus.SERVICE_UNAVAILABLE,
        "AUTHORIZATION_UNAVAILABLE",
        "Company authorization could not be verified");
  }
}
