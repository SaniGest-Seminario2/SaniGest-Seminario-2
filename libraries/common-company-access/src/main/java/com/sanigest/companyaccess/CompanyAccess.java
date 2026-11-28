package com.sanigest.companyaccess;

import java.util.Optional;
import java.util.UUID;

/** Technical queries on behalf of the currently authenticated user. */
public interface CompanyAccess {
  enum Module {
    CATALOG,
    SALES,
    EXPENSE,
    INVENTORY,
    FINANCE
  }

  enum Scope {
    READ,
    WRITE,
    AUDIT
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
  Optional<MembershipContext> membership(UUID companyId, Scope scope);

  /** Company grants audit access to owners and memberships with AUDIT_LOG_READ. */
  default boolean canReadAudit(UUID companyId) {
    return membership(companyId, Scope.AUDIT)
        .filter(member -> companyId.equals(member.companyId()))
        .filter(member -> member.role() == MembershipRole.OWNER || member.explicitGrant())
        .isPresent();
  }

  /** Company checks eligibility and the exact current delegation for the requested tenant. */
  boolean delegated(UUID companyId, Scope scope);
}
