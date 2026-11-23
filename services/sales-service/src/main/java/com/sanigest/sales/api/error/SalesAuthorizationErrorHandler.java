package com.sanigest.sales.api.error;

import com.sanigest.sales.application.exception.SalesAccessForbidden;
import com.sanigest.sales.application.exception.SalesAuthorizationUnavailable;
import com.sanigest.sales.observability.SalesRequestTrace;
import java.net.URI;
import java.time.Instant;
import java.util.Locale;
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

  private static ProblemDetail problem(HttpStatus status, String code, String detail) {
    var problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(URI.create("urn:sanigest:problem:" + code.toLowerCase(Locale.ROOT)));
    problem.setProperty("errorCode", code);
    problem.setProperty("timestamp", Instant.now());
    problem.setProperty("traceId", SalesRequestTrace.current());
    return problem;
  }
}
