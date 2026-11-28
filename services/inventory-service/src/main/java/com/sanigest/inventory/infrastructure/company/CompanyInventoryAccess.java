package com.sanigest.inventory.infrastructure.company;

import com.sanigest.companyaccess.application.exception.CompanyAccessUnavailable;
import com.sanigest.companyaccess.application.port.out.CompanyAccess;
import com.sanigest.companyaccess.security.authentication.AuthenticatedActor;
import com.sanigest.inventory.application.port.out.integration.company.InventoryAccess;
import com.sanigest.inventory.domain.exception.InventoryViolation;
import com.sanigest.inventory.domain.exception.InventoryViolation.Kind;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class CompanyInventoryAccess implements InventoryAccess {
  private final CompanyAccess companies;

  CompanyInventoryAccess(CompanyAccess companies) {
    this.companies = companies;
  }

  @Override
  public UUID authorize(UUID company, Operation operation) {
    var actor = AuthenticatedActor.userId().orElseThrow(CompanyInventoryAccess::denied);
    var scope = operation == Operation.READ ? CompanyAccess.Scope.READ : CompanyAccess.Scope.WRITE;
    try {
      var member =
          companies
              .membership(company, scope)
              .filter(context -> company.equals(context.companyId()))
              .orElseThrow(CompanyInventoryAccess::denied);
      boolean authorized =
          switch (member.role()) {
            case OWNER, ADMIN -> true;
            case VIEWER -> operation == Operation.READ;
            case OPERATOR -> operation == Operation.READ && member.explicitGrant();
          };
      if (!authorized) {
        throw denied();
      }
      return actor;
    } catch (CompanyAccessUnavailable unavailable) {
      throw new InventoryViolation(Kind.UNAVAILABLE, "INVENTORY_AUTHORIZATION_UNAVAILABLE");
    }
  }

  private static InventoryViolation denied() {
    return new InventoryViolation(Kind.FORBIDDEN, "INVENTORY_ACCESS_DENIED");
  }
}
