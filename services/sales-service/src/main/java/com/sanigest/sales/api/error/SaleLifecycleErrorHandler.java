package com.sanigest.sales.api.error;

import static com.sanigest.sales.api.error.SalesProblems.problem;

import com.sanigest.sales.api.controller.SaleLifecycleController;
import com.sanigest.sales.application.exception.InvalidSaleRequest;
import com.sanigest.sales.application.exception.SaleCancellationConflict;
import com.sanigest.sales.application.exception.SaleNotFound;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(0)
@RestControllerAdvice(assignableTypes = SaleLifecycleController.class)
class SaleLifecycleErrorHandler {
  @ExceptionHandler(InvalidSaleRequest.class)
  ProblemDetail invalidRequest() {
    return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The sale request is invalid");
  }

  @ExceptionHandler(SaleNotFound.class)
  ProblemDetail saleNotFound() {
    return problem(HttpStatus.NOT_FOUND, "SALE_NOT_FOUND", "Sale not found");
  }

  @ExceptionHandler(SaleCancellationConflict.class)
  ProblemDetail cancellationConflict() {
    return problem(
        HttpStatus.CONFLICT,
        "SALE_CANCELLATION_CONFLICT",
        "The sale could not be cancelled in its current state");
  }
}
