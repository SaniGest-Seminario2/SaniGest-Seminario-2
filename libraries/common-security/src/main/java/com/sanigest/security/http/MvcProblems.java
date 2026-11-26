package com.sanigest.security.http;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import tools.jackson.databind.DatabindException;

public abstract class MvcProblems extends ResponseEntityExceptionHandler
    implements ResponseBodyAdvice<Object> {
  private static final PathPattern INVITATIONS =
      PathPatternParser.defaultInstance.parse("/api/v1/invitations/**");
  private final String invalidRequestCode;

  protected MvcProblems(String invalidRequestCode) {
    this.invalidRequestCode = invalidRequestCode;
  }

  @ExceptionHandler(AccessDeniedException.class)
  public final void denied(AccessDeniedException failure) {
    throw failure;
  }

  @ExceptionHandler(Exception.class)
  public final ResponseEntity<Object> unexpected(Exception failure, WebRequest request) {
    return handleExceptionInternal(
        failure, null, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
  }

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception failure,
      Object body,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    String code = status.value() == 400 ? invalidRequestCode : HttpProblems.code(status);
    String detail = HttpProblems.detail(status);
    var errors = new LinkedHashMap<String, String>();
    if (failure instanceof MethodArgumentNotValidException invalid) {
      invalid
          .getBindingResult()
          .getFieldErrors()
          .forEach(error -> errors.putIfAbsent(error.getField(), "Invalid or missing value"));
    } else if (failure instanceof HandlerMethodValidationException invalid
        && !invalid.isForReturnValue()) {
      invalid
          .getParameterValidationResults()
          .forEach(result ->
              errors.putIfAbsent(result.getMethodParameter().getParameterName(), "Invalid value"));
    } else if (failure instanceof MethodArgumentTypeMismatchException invalid) {
      errors.put(invalid.getName(), "Invalid value");
    } else if (failure instanceof MissingServletRequestParameterException missing) {
      errors.put(missing.getParameterName(), "Required parameter");
    } else if (failure instanceof MissingRequestHeaderException missing) {
      errors.put(missing.getHeaderName(), "Required header");
    } else if (failure instanceof HttpMessageNotReadableException unreadable) {
      detail =
          unreadable.getCause() == null
              ? "A request body is required"
              : "The request body contains invalid JSON or field values";
      addJsonField(unreadable, errors);
    } else if (failure instanceof TypeMismatchException invalid
        && invalid.getPropertyName() != null) {
      errors.put(invalid.getPropertyName(), "Invalid value");
    }

    var problem = HttpProblems.create(status, code, detail, trace(request.getHeader("X-Trace-Id")));
    if (!errors.isEmpty()) {
      problem.setProperty("errors", errors);
    }
    var responseHeaders = new HttpHeaders();
    responseHeaders.addAll(headers);
    responseHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
    return super.handleExceptionInternal(failure, problem, responseHeaders, status, request);
  }

  private static void addJsonField(
      HttpMessageNotReadableException unreadable, Map<String, String> errors) {
    if (!(unreadable.getCause() instanceof DatabindException invalid)) {
      return;
    }
    StringBuilder field = new StringBuilder();
    for (var reference : invalid.getPath()) {
      String name = reference.getPropertyName();
      if (name != null && name.matches("[a-zA-Z][a-zA-Z0-9_]{0,63}")) {
        if (!field.isEmpty()) {
          field.append('.');
        }
        field.append(name);
      } else if (reference.getIndex() >= 0) {
        field.append('[').append(reference.getIndex()).append(']');
      }
    }
    if (!field.isEmpty()) {
      errors.put(field.toString(), "Invalid value");
    }
  }

  @Override
  public boolean supports(
      MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converter) {
    return ProblemDetail.class.isAssignableFrom(returnType.getParameterType())
        || ResponseEntity.class.isAssignableFrom(returnType.getParameterType());
  }

  @Override
  public Object beforeBodyWrite(
      Object body,
      MethodParameter returnType,
      MediaType mediaType,
      Class<? extends HttpMessageConverter<?>> converter,
      ServerHttpRequest request,
      ServerHttpResponse response) {
    if (body instanceof ProblemDetail problem) {
      var status = HttpStatusCode.valueOf(problem.getStatus());
      var defaults =
          HttpProblems.create(
              status,
              HttpProblems.code(status),
              HttpProblems.detail(status),
              trace(request.getHeaders().getFirst("X-Trace-Id")));
      Map<String, Object> existing = problem.getProperties();
      defaults
          .getProperties()
          .forEach((name, value) -> {
            if (existing == null || !existing.containsKey(name)) {
              problem.setProperty(name, value);
            }
          });
      if (problem.getType() == null || URI.create("about:blank").equals(problem.getType())) {
        problem.setType(HttpProblems.type((String) problem.getProperties().get("errorCode")));
      }
      if (problem.getDetail() == null) {
        problem.setDetail(defaults.getDetail());
      }
      if (INVITATIONS.matches(PathContainer.parsePath(request.getURI().getRawPath()))) {
        problem.setInstance(URI.create("/api/v1/invitations"));
      }
      response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
    }
    return body;
  }

  private static String trace(String header) {
    String trace = MDC.get("traceId");
    return trace == null ? header : trace;
  }
}
