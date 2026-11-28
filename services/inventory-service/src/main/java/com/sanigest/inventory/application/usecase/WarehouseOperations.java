package com.sanigest.inventory.application.usecase;

import com.sanigest.inventory.application.dto.InventoryQueries;
import com.sanigest.inventory.application.port.out.persistence.Warehouses;
import com.sanigest.inventory.domain.exception.InventoryViolation;
import com.sanigest.inventory.domain.model.Warehouse;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WarehouseOperations {
  private final Warehouses warehouses;

  public WarehouseOperations(Warehouses warehouses) {
    this.warehouses = warehouses;
  }

  public Warehouse get(UUID company, UUID id) {
    return require(company, id, false);
  }

  Warehouse require(UUID company, UUID id, boolean lock) {
    return warehouses
        .find(company, id, lock)
        .orElseThrow(
            () -> new InventoryViolation(InventoryViolation.Kind.MISSING, "WAREHOUSE_NOT_FOUND"));
  }

  public InventoryQueries.Page<Warehouse> list(UUID company, InventoryQueries.Warehouses query) {
    return warehouses.list(company, query);
  }

  public Warehouse create(UUID company, Create command) {
    var now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    var created =
        new Warehouse(
            UUID.randomUUID(),
            company,
            command.code(),
            command.name(),
            command.location(),
            Warehouse.Status.ACTIVE,
            now,
            now,
            0);
    if (!warehouses.insert(created)) {
      throw InventoryViolation.conflict("WAREHOUSE_CODE_CONFLICT");
    }
    return created;
  }

  @Transactional
  public Warehouse patch(UUID company, UUID id, Patch command) {
    var old = require(company, id, true);
    if (command.revision() != null && command.revision() != old.revision()) {
      throw InventoryViolation.conflict("INVENTORY_UPDATE_CONFLICT");
    }
    var status = command.status().orElse(old.status());
    var changed =
        new Warehouse(
            id,
            company,
            command.code().orElse(old.code()),
            command.name().orElse(old.name()),
            command.location().orElse(old.location()),
            status,
            old.createdAt(),
            Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS),
            old.revision() + 1);
    if (status == Warehouse.Status.INACTIVE
        && old.status() != status
        && warehouses.hasStock(company, id)) {
      throw InventoryViolation.conflict("WAREHOUSE_NOT_EMPTY");
    }
    warehouses.update(changed);
    return changed;
  }

  public record Create(String code, String name, String location) {}

  public record Field<T>(boolean present, T value) {
    T orElse(T previous) {
      return present ? value : previous;
    }
  }

  public record Patch(
      Field<String> code,
      Field<String> name,
      Field<String> location,
      Field<Warehouse.Status> status,
      Long revision) {}
}
