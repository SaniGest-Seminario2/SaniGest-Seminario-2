package com.sanigest.company.api;

import com.sanigest.company.application.CreateCompanyUseCase.ConflictException;
import com.sanigest.company.application.GetMyCompanyUseCase.CompanyAccessDeniedException;
import com.sanigest.company.application.GetMyCompanyUseCase.CompanyNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {
  @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
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
        HttpStatus.CONFLICT, "The company operation conflicts with an existing resource");
  }

  @ExceptionHandler(CompanyNotFoundException.class)
  ProblemDetail companyNotFound() {
    return problem(HttpStatus.NOT_FOUND, "The authenticated user has no company");
  }

  @ExceptionHandler(CompanyAccessDeniedException.class)
  ProblemDetail companyAccessDenied() {
    return problem(HttpStatus.FORBIDDEN, "The company membership does not allow access");
  }

  private ProblemDetail problem(HttpStatus status, String detail) {
    return ProblemDetail.forStatusAndDetail(status, detail);
  }
}
