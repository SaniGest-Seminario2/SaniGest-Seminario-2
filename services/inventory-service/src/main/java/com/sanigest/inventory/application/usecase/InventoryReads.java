package com.sanigest.inventory.application.usecase;

import com.sanigest.inventory.application.dto.InventoryQueries;
import com.sanigest.inventory.application.port.out.integration.CatalogInventoryClient;
import com.sanigest.inventory.application.port.out.persistence.StockLedger;
import com.sanigest.inventory.domain.StockKey;
import com.sanigest.inventory.domain.StockPosition;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class InventoryReads {
  private final WarehouseOperations warehouses;
  private final StockLedger ledger;
  private final CatalogInventoryClient catalog;

  public InventoryReads(
      WarehouseOperations warehouses, StockLedger ledger, CatalogInventoryClient catalog) {
    this.warehouses = warehouses;
    this.ledger = ledger;
    this.catalog = catalog;
  }

  public Optional<StockPosition> get(StockKey key) {
    warehouses.get(key.companyId(), key.warehouseId());
    var existing = ledger.find(key);
    if (existing.isEmpty()) {
      catalog.find(key.companyId(), key.catalogItemId());
    }
    return existing;
  }

  public InventoryQueries.Page<StockPosition> list(UUID company, InventoryQueries.Stock query) {
    return ledger.list(company, query);
  }

  public InventoryQueries.Page<com.sanigest.inventory.domain.StockMovement> movements(
      UUID company, InventoryQueries.Movements query) {
    return ledger.movements(company, query);
  }

  public InventoryQueries.Summary summary(UUID company, UUID warehouse) {
    if (warehouse != null) {
      warehouses.get(company, warehouse);
    }
    return ledger.summary(company, warehouse);
  }
}
