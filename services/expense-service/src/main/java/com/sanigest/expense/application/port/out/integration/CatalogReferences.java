package com.sanigest.expense.application.port.out.integration;

import java.util.Optional;
import java.util.UUID;

/** Resolves Catalog-owned references within the requesting company. */
public interface CatalogReferences {
  Optional<Supplier> findSupplier(UUID companyId, UUID supplierId);

  Optional<Item> findItem(UUID companyId, UUID itemId);

  record Supplier(UUID id, String name, boolean active, String paymentTerms, Integer creditDays) {}

  record Item(
      UUID id,
      String name,
      boolean active,
      boolean purchasable,
      boolean product,
      boolean divisible) {}
}
