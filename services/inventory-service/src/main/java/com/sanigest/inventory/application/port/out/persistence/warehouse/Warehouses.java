package com.sanigest.inventory.application.port.out.persistence.warehouse;

import com.sanigest.inventory.application.dto.InventoryQueries;
import com.sanigest.inventory.domain.model.warehouse.Warehouse;
import java.util.Optional;
import java.util.UUID;

public interface Warehouses {
  Optional<Warehouse> find(UUID company, UUID id, boolean lock);

  boolean insert(Warehouse warehouse);

  void update(Warehouse warehouse);

  boolean hasStock(UUID company, UUID warehouse);

  InventoryQueries.Page<Warehouse> list(UUID company, InventoryQueries.Warehouses query);
}
