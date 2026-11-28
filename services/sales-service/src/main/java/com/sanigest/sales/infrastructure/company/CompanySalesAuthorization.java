package com.sanigest.sales.infrastructure.company;

import com.sanigest.companyaccess.application.exception.CompanyAccessUnavailable;
import com.sanigest.companyaccess.application.port.out.CompanyAccess;
import com.sanigest.companyaccess.application.port.out.CompanyAccess.MembershipContext;
import com.sanigest.companyaccess.application.port.out.CompanyAccess.Scope;
import com.sanigest.companyaccess.security.authentication.AuthenticatedActor;
import com.sanigest.sales.application.exception.security.SalesAccessForbidden;
import com.sanigest.sales.application.exception.security.SalesAuthorizationUnavailable;
import com.sanigest.sales.application.port.out.integration.company.InvoiceCompanyAccess;
import com.sanigest.sales.application.port.out.integration.company.SalesAuthorization;
import com.sanigest.security.api.request.company.CompanySelector;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
final class CompanySalesAuthorization implements SalesAuthorization, InvoiceCompanyAccess {
  private final ObjectProvider<CompanyAccess> clients;

  @Autowired
  CompanySalesAuthorization(ObjectProvider<CompanyAccess> clients) {
    this(clients, CompanySalesAuthorization::requestedCompany);
  }

  private static UUID requestedCompany() {
    var values =
        RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes request
            ? Collections.list(request.getRequest().getHeaders(CompanySelector.HEADER))
            : List.<String>of();
    return CompanySelector.resolve(values, null, true);
  }

  CompanySalesAuthorization(
      ObjectProvider<CompanyAccess> clients, Supplier<UUID> companySelection) {
    this.clients = clients;
    this.companySelection = companySelection;
  }

  private final Supplier<UUID> companySelection;

  @Override
  public UUID requireMembership(Permission permission) {
    return authorizedMembership(permission).companyId();
  }

  @Override
  public CompanyContext requireInvoiceCompany() {
    var membership = authorizedMembership(Permission.SALES_WRITE);
    return new CompanyContext(membership.companyId(), membership.companyName());
  }

  private MembershipContext authorizedMembership(Permission permission) {
    requireActor();
    UUID companyId = companySelection.get();
    try {
      return client()
          .membership(companyId, scope(permission))
          .filter(membership -> permits(membership, permission))
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
