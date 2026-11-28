package com.sanigest.inventory.application.port.out.integration.catalog;

import java.util.UUID;

public interface CatalogInventoryClient {
  Item find(UUID companyId, UUID itemId);

  record Item(
      UUID id,
      String type,
      boolean active,
      String unitCode,
      Boolean divisible,
      String sku,
      String name) {}
}
