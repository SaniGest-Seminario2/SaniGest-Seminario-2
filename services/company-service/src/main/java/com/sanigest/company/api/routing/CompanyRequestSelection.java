package com.sanigest.company.api.routing;

import com.sanigest.security.CompanySelector;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.UUID;

public final class CompanyRequestSelection {
  private CompanyRequestSelection() {}

  public static UUID resolve(HttpServletRequest request, String companyId) {
    return CompanySelector.resolve(
        Collections.list(request.getHeaders(CompanySelector.HEADER)), companyId, false);
  }
}
