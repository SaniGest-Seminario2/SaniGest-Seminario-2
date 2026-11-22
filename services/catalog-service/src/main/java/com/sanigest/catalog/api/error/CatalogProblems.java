package com.sanigest.catalog.api.error;

import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

final class CatalogProblems {
  private CatalogProblems() {}

  static ProblemDetail problem(HttpStatus status, String code, String detail) {
    var problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setProperty("errorCode", code);
    problem.setProperty("timestamp", Instant.now());
    return problem;
  }
}
