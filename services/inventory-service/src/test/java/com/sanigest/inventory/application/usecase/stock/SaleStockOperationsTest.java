package com.sanigest.inventory.application.usecase.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sanigest.inventory.application.port.out.persistence.stock.SaleStockJournal;
import com.sanigest.inventory.application.port.out.persistence.stock.SaleStockJournal.Issue;
import com.sanigest.inventory.application.port.out.persistence.stock.StockLedger;
import com.sanigest.inventory.application.usecase.warehouse.WarehouseOperations;
import com.sanigest.inventory.domain.exception.InventoryViolation;
import com.sanigest.inventory.domain.model.stock.StockBalance;
import com.sanigest.inventory.domain.model.stock.StockConfiguration;
import com.sanigest.inventory.domain.model.stock.StockKey;
import com.sanigest.inventory.domain.model.stock.StockMovement;
import com.sanigest.inventory.domain.model.stock.StockPosition;
import com.sanigest.inventory.domain.model.warehouse.Warehouse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SaleStockOperationsTest {
  private final UUID sale = UUID.randomUUID();
  private final UUID actor = UUID.randomUUID();
  private final StockKey key =
      new StockKey(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
  private final UUID company = key.companyId();
  private final SaleStockJournal journal = mock();
  private final StockLedger ledger = mock();
  private final WarehouseOperations warehouses = mock();
  private final SaleStockOperations stock = new SaleStockOperations(journal, ledger, warehouses);
  private final Map<UUID, BigDecimal> quantities = Map.of(key.catalogItemId(), BigDecimal.ONE);

  @Test
  void recordsTheIssueAgainstTheTrackedPosition() {
    claimIssue();
    when(journal.tracked(company, quantities.keySet())).thenReturn(List.of(key));
    when(warehouses.require(company, key.warehouseId(), true)).thenReturn(mock(Warehouse.class));
    when(ledger.find(key)).thenReturn(Optional.of(position(true, BigDecimal.TEN)));
    stock.issue(company, sale, actor, quantities);
    assertMovement(StockMovement.Type.SALE_ISSUE, "9");
  }

  @Test
  void skipsUntrackedItemsAndRefusesToChooseBetweenWarehouses() {
    claimIssue();
    stock.issue(company, sale, actor, quantities);
    verifyNoInteractions(ledger);
    when(journal.tracked(company, quantities.keySet())).thenReturn(List.of(key, key));
    assertThatThrownBy(() -> stock.issue(company, sale, actor, quantities))
        .isInstanceOf(InventoryViolation.class)
        .hasMessage("SALE_STOCK_WAREHOUSE_AMBIGUOUS");
  }

  @Test
  void restoresOriginalPositionAndSkipsRepeatedReversal() {
    when(journal.claim(company, sale, null, true)).thenReturn(true, false);
    when(journal.issues(company, sale)).thenReturn(List.of(new Issue(key, BigDecimal.ONE)));
    when(ledger.find(key)).thenReturn(Optional.of(position(false, BigDecimal.ZERO)));
    stock.reverse(company, sale, actor);
    stock.reverse(company, sale, actor);
    assertMovement(StockMovement.Type.SALE_REVERSAL, "1");
  }

  private void claimIssue() {
    when(journal.claim(
            org.mockito.ArgumentMatchers.eq(company),
            org.mockito.ArgumentMatchers.eq(sale),
            any(),
            org.mockito.ArgumentMatchers.eq(false)))
        .thenReturn(true);
  }

  private StockPosition position(boolean tracking, BigDecimal quantity) {
    var time = Instant.EPOCH;
    return new StockPosition(
        key,
        new StockConfiguration(tracking, null, null, null),
        new StockBalance(quantity, BigDecimal.ZERO),
        true,
        "UNIT",
        false,
        null,
        "Producto",
        time,
        time,
        time,
        0,
        0);
  }

  private void assertMovement(StockMovement.Type type, String balance) {
    var captor = ArgumentCaptor.forClass(StockMovement.class);
    verify(ledger).append(captor.capture());
    var movement = captor.getValue();
    assertThat(movement.movementType()).isEqualTo(type);
    assertThat(movement.originId()).isEqualTo(sale);
    assertThat(movement.balanceAfter()).isEqualByComparingTo(balance);
  }
}
