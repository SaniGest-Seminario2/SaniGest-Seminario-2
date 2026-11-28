package com.sanigest.inventory.application.usecase.stock;

import com.sanigest.inventory.application.port.out.persistence.stock.SaleStockJournal;
import com.sanigest.inventory.application.port.out.persistence.stock.StockLedger;
import com.sanigest.inventory.application.usecase.warehouse.WarehouseOperations;
import com.sanigest.inventory.domain.exception.InventoryViolation;
import com.sanigest.inventory.domain.model.stock.Quantity;
import com.sanigest.inventory.domain.model.stock.StockConfiguration;
import com.sanigest.inventory.domain.model.stock.StockKey;
import com.sanigest.inventory.domain.model.stock.StockMovement;
import com.sanigest.inventory.domain.model.stock.StockMovement.Direction;
import com.sanigest.inventory.domain.model.stock.StockMovement.Origin;
import com.sanigest.inventory.domain.model.stock.StockMovement.Type;
import com.sanigest.inventory.domain.model.stock.StockPosition;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SaleStockOperations {
  private final SaleStockJournal journal;
  private final StockLedger ledger;
  private final WarehouseOperations warehouses;

  SaleStockOperations(
      SaleStockJournal journal, StockLedger ledger, WarehouseOperations warehouses) {
    this.journal = journal;
    this.ledger = ledger;
    this.warehouses = warehouses;
  }

  @Transactional
  public void issue(UUID company, UUID sale, UUID actor, Map<UUID, BigDecimal> lines) {
    var quantities = new TreeMap<>(lines);
    quantities.replaceAll((item, amount) -> amount.stripTrailingZeros());
    if (!journal.claim(company, sale, quantities.toString(), false)) {
      return;
    }
    journal.lockWarehouses(company);
    var tracked =
        journal.tracked(company, quantities.keySet()).stream()
            .collect(Collectors.groupingBy(StockKey::catalogItemId));
    for (var line : quantities.entrySet()) {
      var positions = tracked.getOrDefault(line.getKey(), List.of());
      if (positions.size() > 1) {
        throw InventoryViolation.conflict("SALE_STOCK_WAREHOUSE_AMBIGUOUS");
      }
      if (!positions.isEmpty()) {
        var key = positions.getFirst();
        warehouses.require(company, key.warehouseId(), true).requireActive();
        move(key, sale, actor, line.getValue(), false);
      }
    }
  }

  @Transactional
  public void reverse(UUID company, UUID sale, UUID actor) {
    if (!journal.claim(company, sale, null, true)) {
      return;
    }
    journal.lockWarehouses(company);
    for (var issue : journal.issues(company, sale)) {
      move(issue.stock(), sale, actor, issue.quantity(), true);
    }
  }

  private void move(StockKey key, UUID sale, UUID actor, BigDecimal quantity, boolean reverse) {
    var old = ledger.find(key).orElseThrow();
    var amount = Quantity.validate(quantity, old.divisible(), true);
    var direction = reverse ? Direction.IN : Direction.OUT;
    var now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    var configuration = old.configuration();
    if (!configuration.trackingEnabled()) {
      configuration =
          new StockConfiguration(
              true,
              configuration.minimumStock(),
              configuration.maximumStock(),
              configuration.location());
    }
    var updated =
        new StockPosition(
            key,
            configuration,
            old.balance().apply(direction, amount),
            true,
            old.unitCode(),
            old.divisible(),
            old.sku(),
            old.itemName(),
            old.catalogVerifiedAt(),
            old.createdAt(),
            now,
            old.revision() + 1,
            old.movementSequence() + 1);
    ledger.save(updated);
    var id = UUID.randomUUID();
    ledger.append(
        new StockMovement(
            id,
            key,
            reverse ? Type.SALE_REVERSAL : Type.SALE_ISSUE,
            direction,
            amount,
            updated.unitCode(),
            Origin.SALE,
            sale,
            id,
            actor,
            reverse ? "Anulación de venta" : "Salida por venta",
            now,
            now,
            updated.movementSequence(),
            updated.balance().onHand()));
  }
}
