package com.sanigest.sales.api.error;

import com.sanigest.security.http.MvcProblems;
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
