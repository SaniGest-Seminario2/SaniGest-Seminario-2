package com.sanigest.catalog.application.port.out.integration.inventory;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface InventoryAvailability {
  enum State {
    NOT_APPLICABLE,
    NOT_TRACKED,
    OUT,
    LOW,
    AVAILABLE
  }

  void verifyWarehouse(UUID companyId, UUID warehouseId);

  Map<UUID, State> positions(UUID companyId, UUID warehouseId, List<UUID> itemIds);
}
