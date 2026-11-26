package com.sanigest.inventory.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@Tag("regression")
class StockRollbackIT extends InventoryHttpFixture {
  @ParameterizedTest
  @CsvSource({
    "stock_positions,on_hand = 0",
    "stock_movements,quantity = 0",
    "idempotency_keys,result IS NULL"
  })
  void failedInitializationDoesNotLeaveAnyPartialRows(String table, String check) throws Exception {
    var warehouse = warehouse();
    installFailure(table, check);
    try {
      expect("PUT", position(warehouse), company, "retry", config(true, "20"), 500);
      assertThat(count("stock_positions")).isZero();
      assertThat(count("stock_movements")).isZero();
      assertThat(count("idempotency_keys")).isZero();
    } finally {
      removeFailure(table);
    }
    expect("PUT", position(warehouse), company, "retry", config(true, "20"), 200);
    assertThat(physical(warehouse)).isEqualTo("20.000");
    assertThat(count("stock_movements")).isEqualTo(1);
  }

  @ParameterizedTest
  @CsvSource({
    "stock_positions,on_hand <= 5",
    "stock_movements,quantity <> 2",
    "idempotency_keys,result IS NULL"
  })
  void failedAdjustmentRollsBackBalanceMovementAndIdempotency(String table, String check)
      throws Exception {
    var warehouse = warehouse();
    initialize(warehouse, "5");
    installFailure(table, check);
    try {
      expect("POST", ADJUST, company, "retry", adjustment(warehouse, "IN", "2"), 500);
      assertThat(physical(warehouse)).isEqualTo("5.000");
      assertThat(count("stock_movements")).isEqualTo(1);
      assertThat(count("idempotency_keys")).isEqualTo(1);
    } finally {
      removeFailure(table);
    }
    expect("POST", ADJUST, company, "retry", adjustment(warehouse, "IN", "2"), 201);
    assertThat(physical(warehouse)).isEqualTo("7.000");
    assertThat(count("stock_movements")).isEqualTo(2);
  }

  private void installFailure(String table, String check) {
    jdbc.execute(
        "ALTER TABLE inventory."
            + table
            + " ADD CONSTRAINT test_reject CHECK (company_id <> '"
            + company
            + "' OR ("
            + check
            + ")) NOT VALID");
  }

  private void removeFailure(String table) {
    jdbc.execute("ALTER TABLE inventory." + table + " DROP CONSTRAINT test_reject");
  }

  private long count(String table) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM inventory." + table + " WHERE company_id=?", Long.class, company);
  }
}
