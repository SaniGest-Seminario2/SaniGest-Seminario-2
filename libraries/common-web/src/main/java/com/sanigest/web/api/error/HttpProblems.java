package com.sanigest.web.api.error;

import java.net.URI;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

public final class HttpProblems {
  private HttpProblems() {}

  public static ProblemDetail create(
      HttpStatusCode status, String code, String detail, String trace) {
    var problem = create(status, code, detail);
    problem.setProperty("traceId", traceId(trace));
    return problem;
  }

  public static ProblemDetail create(HttpStatusCode status, String code, String detail) {
    var problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(type(code));
    problem.setProperty("errorCode", code);
    problem.setProperty("timestamp", Instant.now().toString());
    return problem;
  }

  public static URI type(String code) {
    return URI.create("urn:sanigest:problem:" + code.toLowerCase(Locale.ROOT));
  }

  public static String traceId(String trace) {
    return trace != null && trace.matches("[0-9a-fA-F]{32}")
        ? trace
        : UUID.randomUUID().toString().replace("-", "");
  }

  public static String code(HttpStatusCode status) {
    return switch (status.value()) {
      case 400 -> "INVALID_REQUEST";
      case 401 -> "AUTHENTICATION_REQUIRED";
      case 403 -> "FORBIDDEN";
      case 404 -> "RESOURCE_NOT_FOUND";
      case 405 -> "METHOD_NOT_ALLOWED";
      case 409 -> "CONFLICT";
      case 415 -> "UNSUPPORTED_MEDIA_TYPE";
      case 500 -> "INTERNAL_ERROR";
      case 503 -> "DEPENDENCY_UNAVAILABLE";
      case 504 -> "UPSTREAM_TIMEOUT";
      default -> "HTTP_" + status.value();
    };
  }

  public static String detail(HttpStatusCode status) {
    return switch (status.value()) {
      case 400 -> "The request contains missing or invalid input";
      case 401 -> "Valid authentication is required";
      case 403 -> "The authenticated account lacks permission for this operation";
      case 404 -> "The requested resource or route was not found";
      case 405 -> "The HTTP method is not supported for this route";
      case 409 -> "The operation conflicts with the current resource state";
      case 415 -> "The request Content-Type is not supported";
      case 500 -> "An unexpected error occurred";
      case 503 -> "A required dependency is unavailable";
      case 504 -> "The upstream service did not respond in time";
      default -> {
        var resolved = HttpStatus.resolve(status.value());
        yield resolved == null ? "The request could not be completed" : resolved.getReasonPhrase();
      }
    };
  }
}
