package com.sanigest.security.api.observability;

import com.sanigest.web.api.error.HttpProblems;
import org.slf4j.MDC;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Resolves one safe correlation identifier per servlet request. */
public final class RequestCorrelation {
  private static final String ATTRIBUTE = RequestCorrelation.class.getName();

  private RequestCorrelation() {}

  public static String current() {
    String trace = MDC.get("traceId");
    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes context) {
      var request = context.getRequest();
      if (request.getAttribute(ATTRIBUTE) instanceof String existing) {
        return existing;
      }
      String forwarded = request.getHeader("X-Trace-Id");
      if (forwarded != null && forwarded.matches("[0-9a-f]{32}")) {
        trace = forwarded;
      }
      String resolved = HttpProblems.traceId(trace);
      request.setAttribute(ATTRIBUTE, resolved);
      return resolved;
    }
    return HttpProblems.traceId(trace);
  }
}
