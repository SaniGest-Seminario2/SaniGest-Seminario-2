package com.sanigest.inventory.application.usecase;

import com.sanigest.inventory.application.dto.StockCommands;
import com.sanigest.inventory.application.port.out.integration.CatalogInventoryClient;
import com.sanigest.inventory.application.port.out.persistence.Idempotency;
import com.sanigest.inventory.application.port.out.persistence.Idempotency.Key;
import com.sanigest.inventory.application.port.out.persistence.Idempotency.Operation;
import com.sanigest.inventory.domain.InventoryViolation;
import com.sanigest.inventory.domain.Quantity;
import com.sanigest.inventory.domain.StockKey;
import com.sanigest.inventory.domain.StockMovement;
import com.sanigest.inventory.domain.StockPosition;
import com.sanigest.inventory.domain.Warehouse;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class StockOperations {
  private static final Logger LOG = LoggerFactory.getLogger(StockOperations.class);
  private final CatalogInventoryClient catalog;
  private final Idempotency idempotency;
  private final StockTransaction writer;

  public StockOperations(
      CatalogInventoryClient catalog, Idempotency idempotency, StockTransaction writer) {
    this.catalog = catalog;
    this.idempotency = idempotency;
    this.writer = writer;
  }

  public StockPosition configure(
      StockKey stock, UUID actor, String token, StockCommands.Configure request) {
    var configuration = request.configuration();
    var initial =
        request.initialQuantity() == null
            ? null
            : Quantity.validate(request.initialQuantity(), true, false);
    if (initial != null && !configuration.trackingEnabled()) {
      throw InventoryViolation.invalid("INITIAL_QUANTITY_REQUIRES_TRACKING");
    }
    var key =
        key(
            stock,
            actor,
            token,
            Operation.INITIALIZE_STOCK,
            initial != null && initial.signum() > 0);
    var hash =
        fingerprint(
            stock,
            configuration.trackingEnabled(),
            configuration.minimumStock(),
            configuration.maximumStock(),
            configuration.location(),
            initial,
            request.revision());
    if (key != null) {
      var previous = idempotency.replay(key, hash, StockPosition.class);
      if (previous.isPresent()) {
        return previous.get();
      }
    }
    var item = product(stock);
    configuration.validateUnit(item.divisible());
    if (initial != null) {
      Quantity.validate(initial, item.divisible(), false);
    }
    return writer.configure(
        stock,
        actor,
        key,
        hash,
        new StockCommands.Configure(configuration, initial, request.revision()),
        item);
  }

  public StockMovement adjust(
      StockKey stock, UUID actor, String token, StockCommands.Adjust request) {
    var key = key(stock, actor, token, Operation.ADJUST_STOCK, true);
    var reason = Warehouse.requiredText(request.reason(), 500);
    var quantity = Quantity.validate(request.quantity(), true, true);
    if (request.direction() == null) {
      throw InventoryViolation.invalid("DIRECTION_REQUIRED");
    }
    var hash = fingerprint(stock, request.direction(), quantity, reason);
    var previous = idempotency.replay(key, hash, StockMovement.class);
    if (previous.isPresent()) {
      return previous.get();
    }
    var item = product(stock);
    Quantity.validate(quantity, item.divisible(), true);
    var result =
        writer.recordMovement(
            stock,
            actor,
            key,
            hash,
            new StockCommands.Adjust(request.direction(), quantity, reason),
            item,
            null);
    LOG.info(
        "Inventory adjustment accepted company={} warehouse={} item={} movement={}",
        stock.companyId(),
        stock.warehouseId(),
        stock.catalogItemId(),
        result.id());
    return result;
  }

  public StockMovement receivePurchase(
      StockKey stock, UUID actor, String token, UUID expenseId, BigDecimal receivedQuantity) {
    var lineId = receiptLineId(token);
    var key = key(stock, actor, token, Operation.PURCHASE_RECEIPT, true);
    var quantity = Quantity.validate(receivedQuantity, true, true);
    var hash = fingerprint(stock, expenseId, quantity);
    var previous = idempotency.replay(key, hash, StockMovement.class);
    if (previous.isPresent()) {
      return previous.get();
    }

    // An expense saved before this endpoint existed may have posted some lines as adjustments.
    var direction = StockMovement.Direction.IN;
    var reason = "Expense receipt line " + lineId;
    var legacyKey = key(stock, actor, token, Operation.ADJUST_STOCK, true);
    var legacyHash = fingerprint(stock, direction, quantity, reason);
    var legacy = idempotency.replay(legacyKey, legacyHash, StockMovement.class);
    if (legacy.isPresent()) {
      var movement = legacy.get();
      if (movement.originType() == StockMovement.Origin.PURCHASE_RECEIPT
          && !expenseId.equals(movement.originId())) {
        throw InventoryViolation.conflict("IDEMPOTENCY_CONFLICT");
      }
      return movement;
    }

    var item = product(stock);
    Quantity.validate(quantity, item.divisible(), true);
    return writer.recordMovement(
        stock,
        actor,
        key,
        hash,
        new StockCommands.Adjust(direction, quantity, reason),
        item,
        new StockTransaction.PurchaseOrigin(expenseId, lineId, legacyKey, legacyHash));
  }

  private static UUID receiptLineId(String token) {
    try {
      var lineId = UUID.fromString(token);
      if (lineId.toString().equals(token)) {
        return lineId;
      }
    } catch (IllegalArgumentException | NullPointerException invalid) {
      throw InventoryViolation.invalid("INVALID_IDEMPOTENCY_KEY");
    }
    throw InventoryViolation.invalid("INVALID_IDEMPOTENCY_KEY");
  }

  private CatalogInventoryClient.Item product(StockKey stock) {
    var item = catalog.find(stock.companyId(), stock.catalogItemId());
    if (!"PRODUCT".equals(item.type())) {
      throw InventoryViolation.invalid("INVENTORY_PRODUCT_REQUIRED");
    }
    if (item.unitCode() == null || item.unitCode().isBlank() || item.divisible() == null) {
      throw InventoryViolation.invalid("INVENTORY_UNIT_REQUIRED");
    }
    return item;
  }

  private static Key key(
      StockKey stock, UUID actor, String token, Operation operation, boolean required) {
    if (token == null && !required) {
      return null;
    }
    if (token == null || !token.matches("[!-~]{1,128}") || token.contains(",")) {
      throw InventoryViolation.invalid("INVALID_IDEMPOTENCY_KEY");
    }
    return new Key(stock.companyId(), actor, operation, token);
  }

  private static String fingerprint(StockKey stock, Object... fields) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      digest.update(
          (stock.companyId().toString() + stock.catalogItemId() + stock.warehouseId())
              .getBytes(StandardCharsets.US_ASCII));
      for (Object field : fields) {
        if (field == null) {
          digest.update((byte) '-');
        } else {
          byte[] bytes = field.toString().getBytes(StandardCharsets.UTF_8);
          digest.update((bytes.length + ":").getBytes(StandardCharsets.US_ASCII));
          digest.update(bytes);
        }
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
