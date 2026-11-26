package com.sanigest.inventory.api.error;

import com.sanigest.inventory.domain.InventoryViolation;
import com.sanigest.inventory.observability.InventoryRequestTrace;
import com.sanigest.security.CompanySelector;
import com.sanigest.security.http.MvcProblems;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class InventoryErrors extends MvcProblems {
  public InventoryErrors() {
    super("INVALID_REQUEST");
  }

  private static final Logger LOG = LoggerFactory.getLogger(InventoryErrors.class);

  @ExceptionHandler(InventoryViolation.class)
  ProblemDetail business(InventoryViolation problem) {
    var status =
        switch (problem.kind()) {
          case INVALID -> HttpStatus.BAD_REQUEST;
          case MISSING -> HttpStatus.NOT_FOUND;
          case CONFLICT -> HttpStatus.CONFLICT;
          case FORBIDDEN -> HttpStatus.FORBIDDEN;
          case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    return response(status, problem.getMessage());
  }

  @ExceptionHandler(CompanySelector.InvalidContext.class)
  ProblemDetail selector(CompanySelector.InvalidContext problem) {
    return response(HttpStatus.BAD_REQUEST, problem.getMessage());
  }

  @ExceptionHandler(ConcurrencyFailureException.class)
  ProblemDetail concurrent() {
    return response(HttpStatus.CONFLICT, "INVENTORY_UPDATE_CONFLICT");
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ProblemDetail integrity() {
    LOG.error(
        "Inventory database integrity assertion failed traceId={}",
        InventoryRequestTrace.current());
    return response(HttpStatus.INTERNAL_SERVER_ERROR, "INVENTORY_INTEGRITY_FAILURE");
  }

  private static ProblemDetail response(HttpStatus status, String code) {
    var trace = InventoryRequestTrace.current();
    if (status == HttpStatus.CONFLICT || status == HttpStatus.SERVICE_UNAVAILABLE) {
      LOG.warn("Inventory request rejected code={} traceId={}", code, trace);
    }
    var problem = ProblemDetail.forStatus(status);
    problem.setProperty("errorCode", code);
    problem.setProperty("timestamp", Instant.now());
    problem.setProperty("traceId", trace);
    return problem;
  }
}
