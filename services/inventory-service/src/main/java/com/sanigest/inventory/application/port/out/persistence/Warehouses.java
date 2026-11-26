package com.sanigest.inventory.application.port.out.persistence;

import com.sanigest.inventory.application.dto.InventoryQueries;
import com.sanigest.inventory.domain.Warehouse;
import java.util.Optional;
import java.util.UUID;

public interface Warehouses {
  Optional<Warehouse> find(UUID company, UUID id, boolean lock);

  boolean insert(Warehouse warehouse);

  void update(Warehouse warehouse);

  boolean hasStock(UUID company, UUID warehouse);

  InventoryQueries.Page<Warehouse> list(UUID company, InventoryQueries.Warehouses query);
}
