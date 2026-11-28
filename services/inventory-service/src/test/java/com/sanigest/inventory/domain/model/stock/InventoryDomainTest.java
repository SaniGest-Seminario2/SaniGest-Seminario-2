package com.sanigest.inventory.domain.model.stock;

import static com.sanigest.inventory.domain.model.stock.Quantity.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sanigest.inventory.domain.exception.InventoryViolation;
import com.sanigest.inventory.domain.model.warehouse.Warehouse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class InventoryDomainTest {
  @ParameterizedTest
  @ValueSource(strings = {"-1", "0", "0.0001", "1.0000", "1000000000000", "1E+100"})
  void rejectsInvalidPositiveQuantities(String value) {
    assertThatThrownBy(() -> Quantity.validate(new BigDecimal(value), true, true))
        .isInstanceOf(InventoryViolation.class);
  }

  @Test
  void checksUnitAndRangeWithoutRounding() {
    assertThat(Quantity.validate(new BigDecimal("2.5"), true, true)).isEqualTo("2.500");
    assertThat(Quantity.validate(new BigDecimal("2.000"), false, true)).isEqualTo("2.000");
    assertThatThrownBy(() -> Quantity.validate(new BigDecimal("2.5"), false, true))
        .hasMessage("INVALID_STOCK_QUANTITY");
    assertThatThrownBy(() ->
            new StockBalance(new BigDecimal("999999999999.999"), ZERO)
                .apply(StockMovement.Direction.IN, new BigDecimal("0.001")))
        .hasMessage("INVALID_STOCK_QUANTITY");
  }

  @ParameterizedTest
  @CsvSource({"false,0,NOT_TRACKED", "true,0,OUT", "true,1,LOW", "true,2,LOW", "true,3,AVAILABLE"})
  void derivesAvailability(boolean tracked, String balance, StockAvailability expected) {
    var config = new StockConfiguration(tracked, new BigDecimal("2"), null, null);
    assertThat(StockAvailability.derive(config, new StockBalance(new BigDecimal(balance), ZERO)))
        .isEqualTo(expected);
  }

  @Test
  void preservesReservationsAndValidatesThresholds() {
    var balance = new StockBalance(new BigDecimal("5"), new BigDecimal("2"));
    assertThat(balance.available()).isEqualTo("3.000");
    assertThat(balance.apply(StockMovement.Direction.OUT, new BigDecimal("3")).available())
        .isEqualTo(ZERO);
    assertThatThrownBy(() -> balance.apply(StockMovement.Direction.OUT, new BigDecimal("4")))
        .hasMessage("INSUFFICIENT_STOCK");
    assertThatThrownBy(() -> new StockBalance(ZERO, BigDecimal.ONE))
        .hasMessage("INSUFFICIENT_STOCK");
    assertThat(new StockConfiguration(true, null, null, null).minimumStock()).isEqualTo(ZERO);
    assertThatThrownBy(() -> new StockConfiguration(true, BigDecimal.TEN, BigDecimal.ONE, null))
        .hasMessage("INVALID_STOCK_THRESHOLDS");
    assertThatThrownBy(() ->
            new StockConfiguration(true, ZERO, new BigDecimal("0.5"), null).validateUnit(false))
        .hasMessage("INVALID_STOCK_QUANTITY");
  }

  @Test
  void normalizesWarehouseAndRejectsInvalidFields() {
    assertThat(warehouse("  alm-1 ", " Principal ", Warehouse.Status.ACTIVE).code())
        .isEqualTo("ALM-1");
    assertThat(warehouse("alm-1", " Principal ", Warehouse.Status.ACTIVE).name())
        .isEqualTo("Principal");
    assertThatThrownBy(() -> warehouse(" ", "Principal", Warehouse.Status.ACTIVE))
        .hasMessage("REQUIRED_TEXT");
    assertThatThrownBy(() -> warehouse("a", null, Warehouse.Status.ACTIVE))
        .hasMessage("REQUIRED_TEXT");
    assertThatThrownBy(() -> warehouse("a b", "Principal", Warehouse.Status.ACTIVE))
        .hasMessage("INVALID_WAREHOUSE");
    assertThatThrownBy(() -> warehouse("a", "Principal", Warehouse.Status.INACTIVE).requireActive())
        .hasMessage("WAREHOUSE_INACTIVE");
    assertThatThrownBy(() -> Warehouse.optionalText("a" + (char) 0, 3)).hasMessage("INVALID_TEXT");
    assertThatThrownBy(() -> Warehouse.optionalText("abcd", 3)).hasMessage("INVALID_TEXT");
  }

  @Test
  void initialMovementCannotBeAnOutflow() {
    var id = UUID.randomUUID();
    assertThatThrownBy(() ->
            new StockMovement(
                id,
                new StockKey(id, id, id),
                StockMovement.Type.INITIAL_STOCK,
                StockMovement.Direction.OUT,
                BigDecimal.ONE,
                "UNIT",
                StockMovement.Origin.STOCK_CONFIGURATION,
                id,
                id,
                id,
                "Inicial",
                Instant.EPOCH,
                Instant.EPOCH,
                1,
                BigDecimal.ONE))
        .hasMessage("INVALID_INITIAL_MOVEMENT");
  }

  private static Warehouse warehouse(String code, String name, Warehouse.Status status) {
    return new Warehouse(
        UUID.randomUUID(),
        UUID.randomUUID(),
        code,
        name,
        null,
        status,
        Instant.EPOCH,
        Instant.EPOCH,
        0);
  }
}
