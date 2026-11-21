package com.sanigest.company.api;

import com.sanigest.company.application.CreateCompanyUseCase.ConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {
  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail validation() {
    return problem(HttpStatus.BAD_REQUEST, "The request is invalid");
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ProblemDetail badRequest() {
    return problem(HttpStatus.BAD_REQUEST, "The request is invalid");
  }

  @ExceptionHandler(ConflictException.class)
  ProblemDetail conflict() {
    return problem(
        HttpStatus.CONFLICT, "The company registration conflicts with an existing resource");
  }

  private ProblemDetail problem(HttpStatus status, String detail) {
    return ProblemDetail.forStatusAndDetail(status, detail);
  }
}
