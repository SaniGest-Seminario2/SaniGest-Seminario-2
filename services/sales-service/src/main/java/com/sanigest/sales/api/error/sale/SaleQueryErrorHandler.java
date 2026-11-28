package com.sanigest.sales.api.error.sale;

import static com.sanigest.sales.api.error.SalesProblems.problem;

import com.sanigest.sales.api.controller.sale.SaleQueryController;
import com.sanigest.sales.application.exception.sale.InvalidSaleQuery;
import com.sanigest.sales.application.exception.sale.SaleListLimitExceeded;
import com.sanigest.sales.application.usecase.sale.ListSales;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(0)
@RestControllerAdvice(assignableTypes = SaleQueryController.class)
class SaleQueryErrorHandler {
  @ExceptionHandler(SaleListLimitExceeded.class)
  ProblemDetail listLimitExceeded() {
    return problem(
        HttpStatus.UNPROCESSABLE_CONTENT,
        "SALE_LIST_LIMIT_EXCEEDED",
        "More than "
            + ListSales.MAX_UNPAGED_RESULTS
            + " sales match. Refine the search or use pagination.");
  }

  @ExceptionHandler(InvalidSaleQuery.class)
  ProblemDetail invalidQuery() {
    return problem(
        HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The sale filters or pagination are invalid");
  }
}
