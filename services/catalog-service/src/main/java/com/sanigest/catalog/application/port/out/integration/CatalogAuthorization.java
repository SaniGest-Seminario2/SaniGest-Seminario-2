package com.sanigest.catalog.application.port.out.integration;

import java.util.Optional;
import java.util.UUID;

public interface CatalogAuthorization {
  enum Permission {
    CATALOG_READ,
    CATALOG_WRITE
  }

  /** Resolves the authenticated actor's authorized tenant from the active membership. */
  Optional<UUID> membership(Permission permission);

  /** Resolves the requested tenant for an operation that supports delegated access. */
  Optional<UUID> delegated(UUID requestedCompany, Permission permission);
}
