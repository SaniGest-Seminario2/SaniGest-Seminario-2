package com.sanigest.company.api.error;

import com.sanigest.company.api.routing.CompanyRoutes;
import com.sanigest.company.application.exception.AuthorizationUnavailableException;
import com.sanigest.company.application.exception.CompanyAccessDeniedException;
import com.sanigest.company.application.exception.CompanyConflictException;
import com.sanigest.company.application.exception.CompanyNotFoundException;
import com.sanigest.company.application.exception.CompanySelectionRequiredException;
import com.sanigest.company.application.exception.InvitationNotFoundException;
import com.sanigest.company.application.exception.LastOwnerException;
import com.sanigest.company.application.exception.MemberNotFoundException;
import com.sanigest.companyaccess.TraceIds;
import com.sanigest.security.CompanySelector.InvalidContext;
import com.sanigest.security.http.MvcProblems;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler extends MvcProblems {
  ApiExceptionHandler() {
    super("INVALID_REQUEST");
  }

  @ExceptionHandler(InvalidContext.class)
  ProblemDetail invalidCompanyContext(HttpServletRequest request, InvalidContext failure) {
    var result = problem(request, HttpStatus.BAD_REQUEST, "Invalid company selection");
    result.setProperty("errorCode", failure.getMessage());
    return result;
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ProblemDetail badRequest(HttpServletRequest request) {
    return problem(request, HttpStatus.BAD_REQUEST, "The request is invalid");
  }

  @ExceptionHandler(CompanyConflictException.class)
  ProblemDetail conflict(HttpServletRequest request) {
    return problem(
        request, HttpStatus.CONFLICT, "The company operation conflicts with an existing resource");
  }

  @ExceptionHandler(CompanySelectionRequiredException.class)
  ProblemDetail companySelectionRequired(HttpServletRequest request) {
    var problem = problem(request, HttpStatus.CONFLICT, "Select a company for this operation");
    problem.setProperty("code", "COMPANY_SELECTION_REQUIRED");
    problem.setProperty("errorCode", "COMPANY_SELECTION_REQUIRED");
    return problem;
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
    String trace = MDC.get("traceId");
    problem.setProperty(
        "traceId", TraceIds.normalize(trace == null ? request.getHeader("X-Trace-Id") : trace));
    if (CompanyRoutes.isInvitationPath(request.getRequestURI())) {
      problem.setInstance(URI.create("/api/v1/invitations"));
    }
    return problem;
  }
}
