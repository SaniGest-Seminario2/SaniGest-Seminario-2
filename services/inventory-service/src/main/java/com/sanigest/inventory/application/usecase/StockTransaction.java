package com.sanigest.inventory.application.usecase;

import com.sanigest.inventory.application.dto.StockCommands;
import com.sanigest.inventory.application.port.out.integration.CatalogInventoryClient.Item;
import com.sanigest.inventory.application.port.out.persistence.Idempotency;
import com.sanigest.inventory.application.port.out.persistence.Idempotency.Key;
import com.sanigest.inventory.application.port.out.persistence.StockLedger;
import com.sanigest.inventory.domain.exception.InventoryViolation;
import com.sanigest.inventory.domain.model.Quantity;
import com.sanigest.inventory.domain.model.StockBalance;
import com.sanigest.inventory.domain.model.StockKey;
import com.sanigest.inventory.domain.model.StockMovement;
import com.sanigest.inventory.domain.model.StockMovement.Direction;
import com.sanigest.inventory.domain.model.StockMovement.Origin;
import com.sanigest.inventory.domain.model.StockMovement.Type;
import com.sanigest.inventory.domain.model.StockPosition;
import com.sanigest.inventory.domain.model.Warehouse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class StockTransaction {
  private final WarehouseOperations warehouses;
  private final StockLedger ledger;
  private final Idempotency idempotency;

  StockTransaction(WarehouseOperations warehouses, StockLedger ledger, Idempotency idempotency) {
    this.warehouses = warehouses;
    this.ledger = ledger;
    this.idempotency = idempotency;
  }

  @Transactional
  public StockPosition configure(
      StockKey stock,
      UUID actor,
      Key key,
      String hash,
      StockCommands.Configure request,
      Item item) {
    var warehouse = warehouses.require(stock.companyId(), stock.warehouseId(), true);
    if (key != null && !idempotency.claim(key, hash)) {
      return idempotency.replay(key, hash, StockPosition.class).orElseThrow();
    }
    var old = ledger.find(stock).orElse(null);
    var configuration = request.configuration();
    boolean enabling = enabling(old, configuration.trackingEnabled());
    verifyWarehouseAndItem(warehouse, configuration.trackingEnabled(), enabling, item.active());
    validateRevision(old, request.revision());
    if (old != null) {
      old.requireConfigurationChange(configuration, request.initialQuantity() != null);
      old.requireCompatibleUnit(item.unitCode(), item.divisible());
    }
    var result = configured(stock, request, item, old, enabling);
    var initial = request.initialQuantity();
    ledger.save(result);
    if (initial != null && initial.signum() > 0) {
      ledger.append(
          movement(
              result,
              actor,
              Type.INITIAL_STOCK,
              Direction.IN,
              initial,
              "Existencia inicial",
              null));
    }
    if (key != null) {
      idempotency.complete(key, result);
    }
    return result;
  }

  @Transactional
  public StockMovement recordMovement(
      StockKey stock,
      UUID actor,
      Key key,
      String hash,
      StockCommands.Adjust command,
      Item item,
      PurchaseOrigin purchase) {
    var warehouse = warehouses.require(stock.companyId(), stock.warehouseId(), true);
    if (!idempotency.claim(key, hash)) {
      return idempotency.replay(key, hash, StockMovement.class).orElseThrow();
    }
    // The legacy key also fences off an older Expense instance retrying during rollout.
    if (purchase != null && !idempotency.claim(purchase.legacyKey(), purchase.legacyHash())) {
      var previous =
          idempotency
              .replay(purchase.legacyKey(), purchase.legacyHash(), StockMovement.class)
              .orElseThrow();
      idempotency.complete(key, previous);
      return previous;
    }
    warehouse.requireActive();
    var old =
        ledger
            .find(stock)
            .filter(position -> position.configuration().trackingEnabled())
            .orElseThrow(() -> InventoryViolation.conflict("INVENTORY_TRACKING_DISABLED"));
    old.requireCompatibleUnit(item.unitCode(), item.divisible());
    old.configuration().validateUnit(item.divisible());
    if (!item.active() && command.direction() != Direction.OUT) {
      throw InventoryViolation.conflict("CATALOG_ITEM_INACTIVE");
    }
    var now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    var updated =
        new StockPosition(
            stock,
            old.configuration(),
            old.balance().apply(command.direction(), command.quantity()),
            true,
            item.unitCode(),
            item.divisible(),
            item.sku(),
            item.name(),
            now,
            old.createdAt(),
            now,
            old.revision() + 1,
            old.movementSequence() + 1);
    var movement =
        movement(
            updated,
            actor,
            purchase == null ? Type.MANUAL_ADJUSTMENT : Type.PURCHASE_RECEIPT,
            command.direction(),
            command.quantity(),
            command.reason(),
            purchase);
    ledger.save(updated);
    ledger.append(movement);
    idempotency.complete(key, movement);
    if (purchase != null) {
      idempotency.complete(purchase.legacyKey(), movement);
    }
    return movement;
  }

  private static boolean enabling(StockPosition old, boolean tracking) {
    return tracking && (old == null || !old.configuration().trackingEnabled());
  }

  private static void verifyWarehouseAndItem(
      Warehouse warehouse, boolean tracking, boolean enabling, boolean active) {
    if (tracking) {
      warehouse.requireActive();
    }
    if (enabling && !active) {
      throw InventoryViolation.conflict("CATALOG_ITEM_INACTIVE");
    }
  }

  private static void validateRevision(StockPosition old, Long revision) {
    if (revision != null && (old == null || revision != old.revision())) {
      throw InventoryViolation.conflict("INVENTORY_UPDATE_CONFLICT");
    }
  }

  private static StockPosition configured(
      StockKey stock,
      StockCommands.Configure request,
      Item item,
      StockPosition old,
      boolean enabling) {
    var balance = old == null ? new StockBalance(Quantity.ZERO, Quantity.ZERO) : old.balance();
    var initial = request.initialQuantity();
    boolean movement = initial != null && initial.signum() > 0;
    var now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    var prior =
        old == null
            ? new PositionAudit(now, -1, 0, false)
            : new PositionAudit(
                old.createdAt(), old.revision(), old.movementSequence(), old.initialized());
    return new StockPosition(
        stock,
        request.configuration(),
        movement ? balance.apply(Direction.IN, initial) : balance,
        enabling || prior.initialized(),
        item.unitCode(),
        item.divisible(),
        item.sku(),
        item.name(),
        now,
        prior.created(),
        now,
        prior.revision() + 1,
        prior.sequence() + (movement ? 1 : 0));
  }

  private record PositionAudit(
      Instant created, long revision, long sequence, boolean initialized) {}

  record PurchaseOrigin(UUID expenseId, UUID lineId, Key legacyKey, String legacyHash) {}

  private static StockMovement movement(
      StockPosition position,
      UUID actor,
      Type type,
      Direction direction,
      BigDecimal quantity,
      String reason,
      PurchaseOrigin purchase) {
    var id = UUID.randomUUID();
    var origin =
        switch (type) {
          case INITIAL_STOCK -> Origin.STOCK_CONFIGURATION;
          case MANUAL_ADJUSTMENT -> Origin.MANUAL_ADJUSTMENT;
          case PURCHASE_RECEIPT -> Origin.PURCHASE_RECEIPT;
        };
    return new StockMovement(
        id,
        position.key(),
        type,
        direction,
        quantity,
        position.unitCode(),
        origin,
        purchase == null ? id : purchase.expenseId(),
        purchase == null ? id : purchase.lineId(),
        actor,
        reason,
        position.updatedAt(),
        position.updatedAt(),
        position.movementSequence(),
        position.balance().onHand());
  }
}
