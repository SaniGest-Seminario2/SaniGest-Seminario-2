package com.sanigest.identity.api;

import com.sanigest.identity.application.AuthenticatedUserNotFoundException;
import com.sanigest.identity.application.DuplicateEmailException;
import com.sanigest.identity.application.InvalidCredentialsException;
import com.sanigest.identity.application.InvalidRefreshTokenException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Profile({"local", "test"})
final class ApiExceptionHandler {

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
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
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

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "The request is invalid");
    problem.setType(VALIDATION_TYPE);
    problem.setTitle("Validation failed");

    Map<String, String> errors = new LinkedHashMap<>();
    exception
        .getBindingResult()
        .getFieldErrors()
        .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
    problem.setProperty("errors", errors);
    return problem;
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ProblemDetail handleInvalidArgument(IllegalArgumentException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    problem.setType(VALIDATION_TYPE);
    problem.setTitle("Validation failed");
    return problem;
  }

  @ExceptionHandler(DuplicateEmailException.class)
  ProblemDetail handleDuplicateEmail(DuplicateEmailException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    problem.setType(DUPLICATE_EMAIL_TYPE);
    problem.setTitle("Email already registered");
    return problem;
  }
}
