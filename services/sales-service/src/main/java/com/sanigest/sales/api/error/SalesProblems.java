package com.sanigest.sales.api.error;

import com.sanigest.sales.observability.SalesRequestTrace;
import java.net.URI;
import java.time.Instant;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

public final class SalesProblems {
  private SalesProblems() {}

  public static ProblemDetail problem(HttpStatus status, String code, String detail) {
    var problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(URI.create("urn:sanigest:problem:" + code.toLowerCase(Locale.ROOT)));
    problem.setProperty("errorCode", code);
    problem.setProperty("timestamp", Instant.now());
    problem.setProperty("traceId", SalesRequestTrace.current());
    return problem;
  }
}
