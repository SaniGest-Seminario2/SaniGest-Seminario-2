package com.sanigest.identity.api.error;

import com.sanigest.identity.application.exception.AuthenticatedUserNotFoundException;
import com.sanigest.identity.application.exception.DuplicateEmailException;
import com.sanigest.identity.application.exception.InvalidCredentialsException;
import com.sanigest.identity.application.exception.InvalidRefreshTokenException;
import com.sanigest.security.http.MvcProblems;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

@RestControllerAdvice
final class ApiExceptionHandler extends MvcProblems {
  ApiExceptionHandler() {
    super("INVALID_REQUEST");
  }

  private static final URI VALIDATION_TYPE = URI.create("urn:sanigest:problem:validation");
  private static final URI DUPLICATE_EMAIL_TYPE =
      URI.create("urn:sanigest:problem:duplicate-email");
  private static final URI UNAUTHORIZED_TYPE = URI.create("urn:sanigest:problem:unauthorized");

  @ExceptionHandler(InvalidCredentialsException.class)
  ProblemDetail handleInvalidCredentials() {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    problem.setType(UNAUTHORIZED_TYPE);
    problem.setTitle("Unauthorized");
    return problem;
  }

  @ExceptionHandler(InvalidRefreshTokenException.class)
  ProblemDetail handleInvalidRefreshToken() {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
    problem.setType(UNAUTHORIZED_TYPE);
    problem.setTitle("Unauthorized");
    return problem;
  }

  @ExceptionHandler(AuthenticatedUserNotFoundException.class)
  ProblemDetail handleAuthenticatedUserNotFound() {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.UNAUTHORIZED, "Authenticated user is unavailable");
    problem.setType(UNAUTHORIZED_TYPE);
    problem.setTitle("Unauthorized");
    return problem;
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException failure,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    var response = super.handleMethodArgumentNotValid(failure, headers, status, request);
    if (response != null && response.getBody() instanceof ProblemDetail problem) {
      problem.setType(VALIDATION_TYPE);
      problem.setTitle("Validation failed");
    }
    return response;
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ProblemDetail handleInvalidArgument() {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "The request is invalid");
    problem.setType(VALIDATION_TYPE);
    problem.setTitle("Validation failed");
    return problem;
  }

  @ExceptionHandler(DuplicateEmailException.class)
  ProblemDetail handleDuplicateEmail() {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Email already registered");
    problem.setType(DUPLICATE_EMAIL_TYPE);
    problem.setTitle("Email already registered");
    return problem;
  }
}
