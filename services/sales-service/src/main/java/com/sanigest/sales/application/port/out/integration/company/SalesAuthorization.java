package com.sanigest.sales.application.port.out.integration.company;

import java.util.UUID;

/** Resolves the authorized company or throws before the operation can proceed. */
public interface SalesAuthorization {
  enum Permission {
    SALES_READ,
    SALES_WRITE,
    SALES_CANCEL
  }

  UUID requireMembership(Permission permission);

  UUID requireDelegation(UUID requestedCompany, Permission permission);
}
