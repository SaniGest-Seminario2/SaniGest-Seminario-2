package com.sanigest.inventory.application.port.out.integration;

import java.util.UUID;

public interface InventoryAccess {
  enum Operation {
    READ,
    MANAGE_WAREHOUSE,
    CONFIGURE_STOCK,
    ADJUST_STOCK
  }

  UUID authorize(UUID company, Operation operation);
}
