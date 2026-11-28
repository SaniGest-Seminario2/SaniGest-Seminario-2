package com.sanigest.expense.application.port.out.integration.inventory;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/** Inventory owns warehouses and the stock entries created from confirmed receipt lines. */
public interface InventoryReceipts {
  Optional<Warehouse> findWarehouse(UUID companyId, UUID warehouseId);

  boolean tracksStock(UUID companyId, UUID warehouseId, UUID itemId);

  /** Records a stock entry with the receipt line ID as its idempotency key. */
  UUID recordEntry(Entry entry);

  record Warehouse(UUID id, boolean active) {}

  record Entry(
      UUID companyId,
      UUID expenseId,
      UUID receiptLineId,
      UUID warehouseId,
      UUID itemId,
      BigDecimal quantity) {}
}
