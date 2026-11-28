package com.sanigest.inventory.api.request;

import com.sanigest.inventory.domain.exception.InventoryViolation;
import com.sanigest.security.api.request.company.CompanySelector;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.UUID;

public final class RequestContext {
  private RequestContext() {}

  public static UUID company(HttpServletRequest request) {
    return CompanySelector.resolve(
        Collections.list(request.getHeaders(CompanySelector.HEADER)), null, true);
  }

  public static String idempotency(HttpServletRequest request) {
    var headers = Collections.list(request.getHeaders("Idempotency-Key"));
    if (headers.size() > 1) {
      throw InventoryViolation.invalid("INVALID_IDEMPOTENCY_KEY");
    }
    return headers.isEmpty() ? null : headers.getFirst();
  }
}
