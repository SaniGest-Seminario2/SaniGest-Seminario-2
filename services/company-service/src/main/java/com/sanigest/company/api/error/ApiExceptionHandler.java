package com.sanigest.company.api.error;

import com.sanigest.company.api.routing.CompanyRoutes;
import com.sanigest.company.application.exception.AuthorizationUnavailableException;
import com.sanigest.company.application.exception.CompanyAccessDeniedException;
import com.sanigest.company.application.exception.CompanyConflictException;
import com.sanigest.company.application.exception.CompanyNotFoundException;
import com.sanigest.company.application.exception.InvitationNotFoundException;
import com.sanigest.company.application.exception.LastOwnerException;
import com.sanigest.company.application.exception.MemberNotFoundException;
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

  @ExceptionHandler(CompanyConflictException.class)
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
    if (CompanyRoutes.ACCEPT_INVITATION.equals(
        request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE))) {
      problem.setInstance(URI.create("/api/v1/invitations"));
    }
    return problem;
  }
}
