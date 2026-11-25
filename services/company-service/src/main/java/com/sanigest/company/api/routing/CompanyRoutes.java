package com.sanigest.company.api.routing;

public final class CompanyRoutes {
  public static final String ACCEPT_INVITATION = "/api/v1/invitations/{token}/accept";

  private CompanyRoutes() {}

  public static boolean isInvitationPath(String path) {
    return path.startsWith("/api/v1/invitations/");
  }
}
