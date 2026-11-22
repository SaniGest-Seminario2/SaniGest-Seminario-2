package com.sanigest.company.api;

import com.sanigest.company.application.AcceptCompanyInvitationUseCase.InvitationNotFoundException;
import com.sanigest.company.application.CompanyMembershipPolicy.LastOwnerException;
import com.sanigest.company.application.CompanyMembershipPolicy.MemberNotFoundException;
import com.sanigest.company.application.CreateCompanyUseCase.ConflictException;
import com.sanigest.company.application.CurrentAccount.AuthorizationUnavailableException;
import com.sanigest.company.application.GetMyCompanyUseCase.CompanyAccessDeniedException;
import com.sanigest.company.application.GetMyCompanyUseCase.CompanyNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.HandlerMapping;

@RestControllerAdvice
class ApiExceptionHandler {
  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  ProblemDetail validation(HttpServletRequest request) {
    return problem(request, HttpStatus.BAD_REQUEST, "The request is invalid");
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ProblemDetail badRequest(HttpServletRequest request) {
    return problem(request, HttpStatus.BAD_REQUEST, "The request is invalid");
  }

  @ExceptionHandler(ConflictException.class)
  ProblemDetail conflict(HttpServletRequest request) {
    return problem(
        request, HttpStatus.CONFLICT, "The company operation conflicts with an existing resource");
  }

  @ExceptionHandler(CompanyNotFoundException.class)
  ProblemDetail companyNotFound(HttpServletRequest request) {
    return problem(request, HttpStatus.NOT_FOUND, "The authenticated user has no company");
  }

  @ExceptionHandler(CompanyAccessDeniedException.class)
  ProblemDetail companyAccessDenied(HttpServletRequest request) {
    return problem(
        request,
        HttpStatus.FORBIDDEN,
        "The authenticated account is not allowed to perform this company operation");
  }

  @ExceptionHandler(AuthorizationUnavailableException.class)
  ProblemDetail authorizationUnavailable(HttpServletRequest request) {
    return problem(
        request, HttpStatus.SERVICE_UNAVAILABLE, "Company authorization could not be verified");
  }

  @ExceptionHandler(InvitationNotFoundException.class)
  ProblemDetail invitationNotFound(HttpServletRequest request) {
    return problem(request, HttpStatus.NOT_FOUND, "Invitation not found");
  }

  @ExceptionHandler(MemberNotFoundException.class)
  ProblemDetail memberNotFound(HttpServletRequest request) {
    return problem(request, HttpStatus.NOT_FOUND, "Company member not found");
  }

  @ExceptionHandler(LastOwnerException.class)
  ProblemDetail lastOwner(HttpServletRequest request) {
    return problem(request, HttpStatus.CONFLICT, "The company must retain an active owner");
  }

  private ProblemDetail problem(HttpServletRequest request, HttpStatus status, String detail) {
    var problem = ProblemDetail.forStatusAndDetail(status, detail);
    if (CompanyTeamController.ACCEPT_PATH.equals(
        request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE))) {
      problem.setInstance(URI.create("/api/v1/invitations"));
    }
    return problem;
  }
}
