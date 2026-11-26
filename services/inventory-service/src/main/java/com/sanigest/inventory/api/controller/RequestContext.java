package com.sanigest.inventory.api.controller;

import com.sanigest.inventory.domain.InventoryViolation;
import com.sanigest.security.CompanySelector;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.UUID;

final class RequestContext {
  private RequestContext() {}

  static UUID company(HttpServletRequest request) {
    return CompanySelector.resolve(
        Collections.list(request.getHeaders(CompanySelector.HEADER)), null, true);
  }

  static String idempotency(HttpServletRequest request) {
    var headers = Collections.list(request.getHeaders("Idempotency-Key"));
    if (headers.size() > 1) {
      throw InventoryViolation.invalid("INVALID_IDEMPOTENCY_KEY");
    }
    return headers.isEmpty() ? null : headers.getFirst();
  }
}
