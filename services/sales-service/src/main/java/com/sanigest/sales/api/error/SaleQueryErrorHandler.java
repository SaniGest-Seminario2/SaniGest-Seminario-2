package com.sanigest.sales.api.error;

import static com.sanigest.sales.api.error.SalesProblems.problem;

import com.sanigest.sales.api.controller.SaleQueryController;
import com.sanigest.sales.application.exception.InvalidSaleQuery;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(0)
@RestControllerAdvice(assignableTypes = SaleQueryController.class)
class SaleQueryErrorHandler {
  @ExceptionHandler(InvalidSaleQuery.class)
  ProblemDetail invalidQuery() {
    return problem(
        HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The sale filters or pagination are invalid");
  }
}
