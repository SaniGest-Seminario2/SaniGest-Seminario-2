package com.sanigest.sales.infrastructure.company;

import com.sanigest.companyaccess.AuthenticatedActor;
import com.sanigest.companyaccess.CompanyAccess;
import com.sanigest.companyaccess.CompanyAccess.MembershipContext;
import com.sanigest.companyaccess.CompanyAccess.Scope;
import com.sanigest.companyaccess.CompanyAccessUnavailable;
import com.sanigest.sales.application.exception.SalesAccessForbidden;
import com.sanigest.sales.application.exception.SalesAuthorizationUnavailable;
import com.sanigest.sales.application.port.out.integration.SalesAuthorization;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
final class CompanySalesAuthorization implements SalesAuthorization {
  private final ObjectProvider<CompanyAccess> clients;

  CompanySalesAuthorization(ObjectProvider<CompanyAccess> clients) {
    this.clients = clients;
  }

  @Override
  public UUID requireMembership(Permission permission) {
    requireActor();
    try {
      return client()
          .membership(scope(permission))
          .filter(membership -> permits(membership, permission))
          .map(MembershipContext::companyId)
          .orElseThrow(SalesAccessForbidden::new);
    } catch (CompanyAccessUnavailable unavailable) {
      throw new SalesAuthorizationUnavailable();
    }
  }

  @Override
  public UUID requireDelegation(UUID requestedCompany, Permission permission) {
    requireActor();
    if (requestedCompany == null || permission == Permission.SALES_CANCEL) {
      throw new SalesAccessForbidden();
    }
    try {
      if (client().delegated(requestedCompany, scope(permission))) {
        return requestedCompany;
      }
      throw new SalesAccessForbidden();
    } catch (CompanyAccessUnavailable unavailable) {
      throw new SalesAuthorizationUnavailable();
    }
  }

  private CompanyAccess client() {
    return clients.getIfAvailable(() -> {
      throw new SalesAuthorizationUnavailable();
    });
  }

  private static void requireActor() {
    if (AuthenticatedActor.userId().isEmpty()) {
      throw new SalesAccessForbidden();
    }
  }

  private static Scope scope(Permission permission) {
    return switch (permission) {
      case SALES_READ -> Scope.READ;
      case SALES_WRITE, SALES_CANCEL -> Scope.WRITE;
    };
  }

  private static boolean permits(MembershipContext membership, Permission permission) {
    return switch (membership.role()) {
      case OWNER, ADMIN -> true;
      case OPERATOR ->
          permission == Permission.SALES_READ
              || (permission == Permission.SALES_WRITE && membership.explicitGrant());
      case VIEWER -> permission == Permission.SALES_READ;
    };
  }
}
