package com.sanigest.expense.api;

import com.sanigest.expense.application.ExpenseFailure;
import com.sanigest.security.CompanySelector;
import com.sanigest.security.http.HttpProblems;
import com.sanigest.security.http.MvcProblems;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ExpenseProblems extends MvcProblems {
  ExpenseProblems() {
    super("EXPENSE_INVALID_REQUEST");
  }

  @ExceptionHandler(ExpenseFailure.class)
  ProblemDetail expense(ExpenseFailure failure) {
    var status =
        switch (failure.kind()) {
          case INVALID -> HttpStatus.BAD_REQUEST;
          case CONFLICT -> HttpStatus.CONFLICT;
          case FORBIDDEN -> HttpStatus.FORBIDDEN;
          case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    return HttpProblems.create(status, failure.getMessage(), HttpProblems.detail(status), null);
  }

  @ExceptionHandler(CompanySelector.InvalidContext.class)
  ProblemDetail selection(CompanySelector.InvalidContext failure) {
    return HttpProblems.create(
        HttpStatus.BAD_REQUEST, failure.getMessage(), "Provide a valid company selection", null);
  }
}
