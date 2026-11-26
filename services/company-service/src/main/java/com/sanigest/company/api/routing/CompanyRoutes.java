package com.sanigest.company.api.routing;

import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

public final class CompanyRoutes {
  public static final String ACCEPT_INVITATION = "/api/v1/invitations/{token}/accept";

  private CompanyRoutes() {}

  private static final PathPattern INVITATIONS =
      PathPatternParser.defaultInstance.parse("/api/v1/invitations/**");

  public static boolean isInvitationPath(String path) {
    return INVITATIONS.matches(PathContainer.parsePath(path));
  }
}
