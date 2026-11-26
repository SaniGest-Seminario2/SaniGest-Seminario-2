package com.sanigest.inventory.observability;

import com.sanigest.companyaccess.TraceIds;
import org.slf4j.MDC;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class InventoryRequestTrace {
  private static final String ATTRIBUTE = InventoryRequestTrace.class.getName();

  private InventoryRequestTrace() {}

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
