package com.sanigest.inventory.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sanigest.inventory.application.dto.StockCommands;
import com.sanigest.inventory.application.port.out.integration.CatalogInventoryClient;
import com.sanigest.inventory.application.port.out.integration.CatalogInventoryClient.Item;
import com.sanigest.inventory.application.port.out.persistence.Idempotency;
import com.sanigest.inventory.application.port.out.persistence.StockLedger;
import com.sanigest.inventory.application.port.out.persistence.Warehouses;
import com.sanigest.inventory.domain.exception.InventoryViolation;
import com.sanigest.inventory.domain.model.StockConfiguration;
import com.sanigest.inventory.domain.model.StockKey;
import com.sanigest.inventory.domain.model.StockMovement;
import com.sanigest.inventory.domain.model.StockPosition;
import com.sanigest.inventory.domain.model.Warehouse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StockOperationsTest {
  private final StockKey key =
      new StockKey(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
  private final UUID actor = UUID.randomUUID();
  private final Warehouses warehouses = mock(Warehouses.class);
  private final StockLedger ledger = mock(StockLedger.class);
  private final Idempotency idempotency = mock(Idempotency.class);
  private final CatalogInventoryClient catalog = mock(CatalogInventoryClient.class);
  private final WarehouseOperations warehouseOperations = new WarehouseOperations(warehouses);
  private final StockOperations operations =
      new StockOperations(
          catalog, idempotency, new StockTransaction(warehouseOperations, ledger, idempotency));

  @BeforeEach
  void setup() {
    when(warehouses.find(key.companyId(), key.warehouseId(), true))
        .thenReturn(
            Optional.of(
                new Warehouse(
                    key.warehouseId(),
                    key.companyId(),
                    "MAIN",
                    "Main",
                    null,
                    Warehouse.Status.ACTIVE,
                    Instant.EPOCH,
                    Instant.EPOCH,
                    0)));
    when(catalog.find(key.companyId(), key.catalogItemId())).thenReturn(product(true, "UNIT"));
    when(idempotency.claim(any(), any())).thenReturn(true);
  }

  @Test
  void initializesExactlyOnceAndPreservesBalanceOnEdit() {
    var result = operations.configure(key, actor, "first", configuration(true, "20", null));
    assertThat(result.initialized()).isTrue();
    assertThat(result.balance().onHand()).isEqualTo("20.000");
    var captor = ArgumentCaptor.forClass(StockMovement.class);
    verify(ledger).append(captor.capture());
    assertThat(captor.getValue().movementType()).isEqualTo(StockMovement.Type.INITIAL_STOCK);
    verify(idempotency).complete(any(), eq(result));
    when(ledger.find(key)).thenReturn(Optional.of(result));
    var edit = operations.configure(key, actor, null, configuration(true, null, result.revision()));
    assertThat(edit.balance()).isEqualTo(result.balance());
    assertThatThrownBy(
            () -> operations.configure(key, actor, "new", configuration(true, "0", null)))
        .hasMessage("INITIAL_STOCK_ALREADY_REGISTERED");
    assertThatThrownBy(
            () -> operations.configure(key, actor, null, configuration(false, null, null)))
        .hasMessage("INVENTORY_TRACKING_HAS_STOCK");
    assertThatThrownBy(() -> operations.configure(key, actor, null, configuration(true, null, 99L)))
        .hasMessage("INVENTORY_UPDATE_CONFLICT");
  }

  @Test
  void zeroInitializationSurvivesDisableAndEnable() {
    var first = operations.configure(key, actor, null, configuration(true, null, null));
    assertThat(first.initialized()).isTrue();
    when(ledger.find(key)).thenReturn(Optional.of(first));
    var disabled = operations.configure(key, actor, null, configuration(false, null, null));
    assertThat(disabled.initialized()).isTrue();
    when(ledger.find(key)).thenReturn(Optional.of(disabled));
    assertThat(
            operations
                .configure(key, actor, null, configuration(true, null, null))
                .movementSequence())
        .isZero();
    assertThatThrownBy(() ->
            operations.adjust(key, actor, "disabled", adjustment(StockMovement.Direction.IN, "1")))
        .hasMessage("INVENTORY_TRACKING_DISABLED");
  }

  @Test
  void replayRetainsOriginalResultAndNormalizesDecimals() {
    var first = operations.configure(key, actor, "initial", configuration(true, "20.0", null));
    var fingerprint = ArgumentCaptor.forClass(String.class);
    verify(idempotency).claim(any(), fingerprint.capture());
    when(idempotency.replay(any(), eq(fingerprint.getValue()), eq(StockPosition.class)))
        .thenReturn(Optional.of(first));
    assertThat(operations.configure(key, actor, "initial", configuration(true, "20.000", null)))
        .isEqualTo(first);
  }

  @Test
  void adjustsInactiveProductOnlyOutwardAndRejectsChangedUnit() {
    var first = operations.configure(key, actor, "initial", configuration(true, "5", null));
    when(ledger.find(key)).thenReturn(Optional.of(first));
    when(catalog.find(key.companyId(), key.catalogItemId())).thenReturn(product(false, "UNIT"));
    var movement =
        operations.adjust(key, actor, "out", adjustment(StockMovement.Direction.OUT, "2"));
    assertThat(movement.balanceAfter()).isEqualTo("3.000");
    assertThat(movement.sequence()).isEqualTo(2);
    assertThatThrownBy(
            () -> operations.adjust(key, actor, "in", adjustment(StockMovement.Direction.IN, "2")))
        .hasMessage("CATALOG_ITEM_INACTIVE");
    when(catalog.find(key.companyId(), key.catalogItemId())).thenReturn(product(true, "PACK"));
    assertThatThrownBy(() ->
            operations.adjust(key, actor, "changed", adjustment(StockMovement.Direction.OUT, "1")))
        .hasMessage("INVENTORY_UNIT_CHANGED");
  }

  @Test
  void recordsVerifiedUnitWhenNoPreviousMovementExists() {
    var first = operations.configure(key, actor, null, configuration(true, null, null));
    when(ledger.find(key)).thenReturn(Optional.of(first));
    when(catalog.find(key.companyId(), key.catalogItemId())).thenReturn(product(true, "PACK"));
    var movement =
        operations.adjust(key, actor, "first", adjustment(StockMovement.Direction.IN, "2"));
    assertThat(movement.unitCode()).isEqualTo("PACK");
    assertThat(movement.sequence()).isEqualTo(1);
  }

  @Test
  void rejectsBadReferencesAndMissingKeyBeforeWriting() {
    assertThatThrownBy(() -> operations.configure(key, actor, null, configuration(true, "2", null)))
        .hasMessage("INVALID_IDEMPOTENCY_KEY");
    assertThatThrownBy(
            () -> operations.configure(key, actor, null, configuration(false, "0", null)))
        .hasMessage("INITIAL_QUANTITY_REQUIRES_TRACKING");
    when(catalog.find(key.companyId(), key.catalogItemId()))
        .thenReturn(new Item(key.catalogItemId(), "SERVICE", true, null, null, null, "Servicio"));
    assertThatThrownBy(
            () -> operations.configure(key, actor, null, configuration(true, null, null)))
        .hasMessage("INVENTORY_PRODUCT_REQUIRED");
    when(catalog.find(key.companyId(), key.catalogItemId())).thenReturn(product(true, null));
    assertThatThrownBy(
            () -> operations.configure(key, actor, null, configuration(true, null, null)))
        .hasMessage("INVENTORY_UNIT_REQUIRED");
    when(catalog.find(key.companyId(), key.catalogItemId()))
        .thenThrow(
            new InventoryViolation(InventoryViolation.Kind.UNAVAILABLE, "CATALOG_UNAVAILABLE"));
    assertThatThrownBy(
            () -> operations.configure(key, actor, null, configuration(true, null, null)))
        .hasMessage("CATALOG_UNAVAILABLE");
    verifyNoInteractions(ledger);
  }

  @Test
  void reconcilesWithoutRepairing() {
    var reconcile = new ReconcileStock(ledger);
    assertThat(reconcile.check(key)).isEmpty();
    when(ledger.reconcile(key))
        .thenReturn(Optional.of(new StockLedger.Reconciliation(BigDecimal.ONE, BigDecimal.ONE)));
    assertThat(reconcile.check(key).orElseThrow().difference()).isZero();
    when(ledger.reconcile(key))
        .thenReturn(Optional.of(new StockLedger.Reconciliation(BigDecimal.TEN, BigDecimal.ONE)));
    assertThat(reconcile.check(key).orElseThrow().difference()).isEqualTo("9");
  }

  private Item product(boolean active, String unit) {
    return new Item(key.catalogItemId(), "PRODUCT", active, unit, false, "ITEM", "Product");
  }

  private static StockCommands.Configure configuration(
      boolean tracked, String initial, Long revision) {
    return new StockCommands.Configure(
        new StockConfiguration(tracked, null, null, null),
        initial == null ? null : new BigDecimal(initial),
        revision);
  }

  private static StockCommands.Adjust adjustment(StockMovement.Direction direction, String amount) {
    return new StockCommands.Adjust(direction, new BigDecimal(amount), "Correction");
  }
}
