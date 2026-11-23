package com.sanigest.sales.api.error;

import static com.sanigest.sales.api.error.SalesProblems.problem;

import com.sanigest.sales.api.controller.ReceivableController;
import com.sanigest.sales.application.exception.ReceivableNotFound;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = ReceivableController.class)
class ReceivableErrorHandler {
  @ExceptionHandler(ReceivableNotFound.class)
  ProblemDetail missing() {
    return problem(HttpStatus.NOT_FOUND, "RECEIVABLE_NOT_FOUND", "The receivable was not found");
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ProblemDetail invalidId() {
    return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The receivable ID is invalid");
  }
}
