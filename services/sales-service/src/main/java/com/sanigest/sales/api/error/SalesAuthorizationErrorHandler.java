package com.sanigest.sales.api.error;

import static com.sanigest.sales.api.error.SalesProblems.problem;

import com.sanigest.sales.application.exception.SalesAccessForbidden;
import com.sanigest.sales.application.exception.SalesAuthorizationUnavailable;
import com.sanigest.security.CompanySelector.InvalidContext;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(0)
@RestControllerAdvice
class SalesAuthorizationErrorHandler {
  @ExceptionHandler(InvalidContext.class)
  ProblemDetail invalidCompanyContext(InvalidContext failure) {
    return problem(HttpStatus.BAD_REQUEST, failure.getMessage(), "Invalid company selection");
  }

  @ExceptionHandler(SalesAccessForbidden.class)
  ProblemDetail forbidden() {
    return problem(HttpStatus.FORBIDDEN, "FORBIDDEN", "Sales access denied");
  }

  @ExceptionHandler(SalesAuthorizationUnavailable.class)
  ProblemDetail unavailable() {
    return problem(
        HttpStatus.SERVICE_UNAVAILABLE,
        "AUTHORIZATION_UNAVAILABLE",
        "Sales authorization unavailable");
  }
}
