package com.sanigest.catalog.api.error;

import com.sanigest.security.api.error.MvcProblems;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
class HttpProblemAdvice extends MvcProblems {
  HttpProblemAdvice() {
    super("INVALID_REQUEST");
  }
}
