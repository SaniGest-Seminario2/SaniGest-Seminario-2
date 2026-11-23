package com.sanigest.companyaccess;

import java.util.Optional;
import java.util.UUID;

/** Technical queries on behalf of the currently authenticated user. */
public interface CompanyAccess {
  enum Module {
    CATALOG,
    SALES,
    EXPENSE,
    FINANCE
  }

  enum Scope {
    READ,
    WRITE
  }

  enum MembershipRole {
    OWNER,
    ADMIN,
    OPERATOR,
    VIEWER
  }

  record MembershipContext(
      UUID companyId, MembershipRole role, boolean explicitGrant, String companyName) {
    public MembershipContext(UUID companyId, MembershipRole role, boolean explicitGrant) {
      this(companyId, role, explicitGrant, null);
    }
  }

  /** Company resolves the active membership. The caller applies its operation policy. */
  Optional<MembershipContext> membership(Scope scope);

  /** Company checks eligibility and the exact current delegation for the requested tenant. */
  boolean delegated(UUID companyId, Scope scope);
}
