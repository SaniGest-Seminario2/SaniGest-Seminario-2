package com.sanigest.sales.api.error;

import static com.sanigest.sales.api.error.SalesProblems.problem;

import com.sanigest.sales.application.exception.SalesAccessForbidden;
import com.sanigest.sales.application.exception.SalesAuthorizationUnavailable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class SalesAuthorizationErrorHandler {
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
