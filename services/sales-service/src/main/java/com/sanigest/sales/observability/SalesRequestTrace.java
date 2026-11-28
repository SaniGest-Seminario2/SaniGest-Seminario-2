package com.sanigest.sales.observability;

import com.sanigest.companyaccess.observability.TraceIds;
import org.slf4j.MDC;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class SalesRequestTrace {
  private static final String ATTRIBUTE = SalesRequestTrace.class.getName();

  private SalesRequestTrace() {}

  public static String current() {
    String traceId = MDC.get("traceId");
    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes context) {
      var request = context.getRequest();
      if (request.getAttribute(ATTRIBUTE) instanceof String existing) {
        return existing;
      }
      String resolved =
          TraceIds.normalize(traceId == null ? request.getHeader("X-Trace-Id") : traceId);
      request.setAttribute(ATTRIBUTE, resolved);
      return resolved;
    }
    return TraceIds.normalize(traceId);
  }
}
