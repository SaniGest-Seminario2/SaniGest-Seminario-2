package com.sanigest.catalog.infrastructure.company;

import com.sanigest.catalog.application.CatalogAuthorization;
import com.sanigest.catalog.application.ClientCreationException;
import com.sanigest.catalog.application.ClientCreationException.Reason;
import com.sanigest.companyaccess.AuthenticatedActor;
import com.sanigest.companyaccess.CompanyAccess;
import com.sanigest.companyaccess.CompanyAccess.MembershipContext;
import com.sanigest.companyaccess.CompanyAccess.Scope;
import com.sanigest.companyaccess.CompanyAccessUnavailable;
import java.util.Optional;
import java.util.UUID;

final class CompanyCatalogAuthorization implements CatalogAuthorization {
  private final CompanyAccess access;

  CompanyCatalogAuthorization(CompanyAccess access) {
    this.access = access;
  }

  @Override
  public Optional<UUID> membership(Permission permission) {
    if (AuthenticatedActor.userId().isEmpty()) {
      return Optional.empty();
    }
    try {
      return access
          .membership(scope(permission))
          .filter(membership -> permits(membership, permission))
          .map(MembershipContext::companyId);
    } catch (CompanyAccessUnavailable unavailable) {
      throw new ClientCreationException(Reason.AUTHORIZATION_UNAVAILABLE);
    }
  }

  @Override
  public Optional<UUID> delegated(UUID requestedCompany, Permission permission) {
    if (requestedCompany == null || AuthenticatedActor.userId().isEmpty()) {
      return Optional.empty();
    }
    try {
      return access.delegated(requestedCompany, scope(permission))
          ? Optional.of(requestedCompany)
          : Optional.empty();
    } catch (CompanyAccessUnavailable unavailable) {
      throw new ClientCreationException(Reason.AUTHORIZATION_UNAVAILABLE);
    }
  }

  private static Scope scope(Permission permission) {
    return switch (permission) {
      case CATALOG_READ -> Scope.READ;
      case CATALOG_WRITE -> Scope.WRITE;
    };
  }

  private static boolean permits(MembershipContext membership, Permission permission) {
    return switch (membership.role()) {
      case OWNER, ADMIN -> true;
      case OPERATOR -> permission == Permission.CATALOG_READ || membership.explicitGrant();
      case VIEWER -> permission == Permission.CATALOG_READ;
    };
  }
}
