package com.sanigest.inventory.application.port.out.persistence;

import com.sanigest.inventory.application.dto.InventoryQueries;
import com.sanigest.inventory.application.dto.InventoryQueries.StockEntry;
import com.sanigest.inventory.domain.StockKey;
import com.sanigest.inventory.domain.StockMovement;
import com.sanigest.inventory.domain.StockPosition;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface StockLedger {
  Optional<StockPosition> find(StockKey key);

  void save(StockPosition position);

  void append(StockMovement movement);

  InventoryQueries.Page<StockEntry> list(UUID company, InventoryQueries.Stock query);

  InventoryQueries.Page<StockMovement> movements(UUID company, InventoryQueries.Movements query);

  InventoryQueries.Summary summary(UUID company, UUID warehouse);

  Optional<Reconciliation> reconcile(StockKey key);

  record Reconciliation(BigDecimal persisted, BigDecimal reconstructed) {
    public BigDecimal difference() {
      return persisted.subtract(reconstructed);
    }
  }
}
