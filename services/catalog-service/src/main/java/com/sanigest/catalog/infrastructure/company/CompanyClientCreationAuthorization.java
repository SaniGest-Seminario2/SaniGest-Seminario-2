package com.sanigest.catalog.infrastructure.company;

import com.sanigest.catalog.application.ClientCreationAuthorization;
import com.sanigest.catalog.application.ClientCreationException;
import com.sanigest.catalog.application.ClientCreationException.Reason;
import com.sanigest.companyaccess.AuthenticatedActor;
import com.sanigest.companyaccess.CompanyAccess;
import com.sanigest.companyaccess.CompanyAccess.MembershipContext;
import com.sanigest.companyaccess.CompanyAccess.Scope;
import com.sanigest.companyaccess.CompanyAccessUnavailable;
import java.util.Optional;
import java.util.UUID;

final class CompanyClientCreationAuthorization implements ClientCreationAuthorization {
  private final CompanyAccess access;

  CompanyClientCreationAuthorization(CompanyAccess access) {
    this.access = access;
  }

  @Override
  public Optional<UUID> authorizedCompany(UUID userId, String accessToken) {
    if (!AuthenticatedActor.matches(userId, accessToken)) {
      return Optional.empty();
    }
    try {
      return access
          .membership(Scope.WRITE)
          .filter(CompanyClientCreationAuthorization::canCreate)
          .map(MembershipContext::companyId);
    } catch (CompanyAccessUnavailable unavailable) {
      throw new ClientCreationException(Reason.AUTHORIZATION_UNAVAILABLE);
    }
  }

  private static boolean canCreate(MembershipContext membership) {
    return switch (membership.role()) {
      case OWNER, ADMIN -> true;
      case OPERATOR -> membership.explicitGrant();
      case VIEWER -> false;
    };
  }
}
