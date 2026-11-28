package com.sanigest.inventory.application.port.out.persistence.stock;

import com.sanigest.inventory.application.dto.InventoryQueries;
import com.sanigest.inventory.application.dto.InventoryQueries.StockEntry;
import com.sanigest.inventory.domain.model.stock.StockKey;
import com.sanigest.inventory.domain.model.stock.StockMovement;
import com.sanigest.inventory.domain.model.stock.StockPosition;
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
