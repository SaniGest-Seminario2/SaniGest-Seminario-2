package com.sanigest.inventory.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sanigest.inventory.domain.StockKey;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

class StockLedgerIT extends InventoryHttpFixture {
  @Test
  @Tag("smoke")
  void tracksInitialStockAndAdjustmentsThroughSecuredHttp() throws Exception {
    var warehouse = warehouse();
    var initial = initialize(warehouse, "20");
    assertThat(initial.get("onHand").asString()).isEqualTo("20.000");
    assertThat(initial.get("updatedAt").isString()).isTrue();
    assertThat(
            expect("GET", "/actuator/info", null, null, null, 200)
                .get("build")
                .get("artifact")
                .asString())
        .isEqualTo("inventory-service");
    assertThat(expect("GET", "/inventory-openapi.json", null, null, null, 200).has("paths"))
        .isTrue();
    var in = expect("POST", ADJUST, company, "in", adjustment(warehouse, "IN", "5.0"), 201);
    expect("POST", ADJUST, company, "out", adjustment(warehouse, "OUT", "3"), 201);
    assertThat(
            expect("GET", position(warehouse), company, null, null, 200).get("onHand").asString())
        .isEqualTo("22.000");
    assertThat(expect("POST", ADJUST, company, "in", adjustment(warehouse, "IN", "5.000"), 201))
        .isEqualTo(in);
    assertThat(in.get("balanceAfter").asString()).isEqualTo("25.000");
    assertThat(initialize(warehouse, "20.000")).isEqualTo(initial);
    var movements =
        expect(
            "GET",
            MOVEMENTS + "?warehouseId=" + warehouse + "&order=ASC",
            company,
            null,
            null,
            200);
    assertThat(movements.get("totalElements").asInt()).isEqualTo(3);
    assertThat(movements.get("content").get(0).get("movementType").asString())
        .isEqualTo("INITIAL_STOCK");
    assertThat(movements.get("content").get(2).get("sequence").asLong()).isEqualTo(3);
    assertThat(expect("GET", SUMMARY, company, null, null, 200).get("trackedPositions").asInt())
        .isEqualTo(1);
    var service = UUID.randomUUID();
    catalog(company, service, "SERVICE", true, "UNIT", false);
    expect("POST", ADJUST, company, "service", adjustment(service, warehouse, "IN", "1"), 400);
    expect("GET", position(warehouse), other, null, null, 404);
    expect("GET", MOVEMENTS, null, null, null, 400);
    assertThat(send("GET", MOVEMENTS, company, null, null, null).statusCode()).isEqualTo(401);
    assertThat(reconcile.check(new StockKey(company, item, warehouse)).orElseThrow().difference())
        .isZero();
    assertThat(physical(warehouse)).isEqualTo("22.000");
    assertThat(countMovements(warehouse)).isEqualTo(3);
  }

  @Test
  @Tag("regression")
  void filtersBeforePaginationAndMaintainsHistoricalBalances() throws Exception {
    var first = warehouse();
    var second =
        UUID.fromString(
            expect(
                    "POST",
                    WAREHOUSES,
                    company,
                    null,
                    "{\"code\":\"ALM-2\",\"name\":\"Second\"}",
                    201)
                .get("id")
                .asString());
    initialize(first, "5");
    expect("PUT", position(second), company, null, config(true, null), 200);
    expect(
        "PUT",
        position(first),
        company,
        null,
        "{\"trackingEnabled\":true,\"minimumStock\":\"5\"}",
        200);
    var low =
        expect("GET", STOCK + "?availability=LOW&size=1&search=sku-001", company, null, null, 200);
    assertThat(low.get("totalElements").asInt()).isEqualTo(1);
    var batch =
        STOCK + "?warehouseId=" + first + "&catalogItemIds=" + item + "," + UUID.randomUUID();
    var selected = expect("GET", batch + "&availability=LOW&size=1", company, null, null, 200);
    assertThat(selected.path("totalElements").asInt()).isEqualTo(1);
    assertThat(selected.at("/content/0/catalogItemId").asString()).isEqualTo(item.toString());
    assertThat(selected.at("/content/0/warehouseName").isString()).isTrue();
    expect("GET", STOCK + "?catalogItemIds=" + item, company, null, null, 400);
    expect("GET", batch, other, null, null, 404);
    expect("GET", batch, UUID.randomUUID(), null, null, 403);
    assertThat(low.get("content").get(0).get("warehouseId").asString()).isEqualTo(first.toString());
    assertThat(
            expect("GET", STOCK + "?size=1&page=2", company, null, null, 200)
                .get("totalElements")
                .asInt())
        .isEqualTo(2);
    assertThat(expect("GET", SUMMARY, company, null, null, 200).get("outPositions").asInt())
        .isEqualTo(1);
    expect("PUT", position(second), company, null, config(false, null), 200);
    expect("PUT", position(second), company, null, config(true, null), 200);
    assertThat(
            expect("PUT", position(second), company, null, config(true, "0"), 409)
                .get("errorCode")
                .asString())
        .isEqualTo("INITIAL_STOCK_ALREADY_REGISTERED");
    expect("PUT", position(first), company, null, config(false, null), 409);
    expect("PATCH", WAREHOUSES + "/" + first, company, null, "{\"status\":\"INACTIVE\"}", 409);
    expect("PATCH", WAREHOUSES + "/" + second, company, null, "{\"status\":\"INACTIVE\"}", 200);
    expect("POST", ADJUST, company, "inactive", adjustment(second, "IN", "1"), 409);
    expect("PATCH", WAREHOUSES + "/" + second, company, null, "{\"status\":\"ACTIVE\"}", 200);
    assertThat(countMovements(second)).isZero();
    assertThat(physical(first)).isEqualTo("5.000");
  }

  @Test
  @Tag("regression")
  void enforcesDatabaseConstraintsAndDetectsDivergence() throws Exception {
    var warehouse = warehouse();
    catalog(company, item, "PRODUCT", true, "KG", true);
    initialize(warehouse, "2.375");
    jdbc.update(
        "UPDATE inventory.stock_positions SET reserved=2 WHERE company_id=? AND warehouse_id=?",
        company,
        warehouse);
    expect("POST", ADJUST, company, "reserved", adjustment(warehouse, "OUT", "0.376"), 409);
    expect("POST", ADJUST, company, "exact", adjustment(warehouse, "OUT", "0.375"), 201);
    assertThat(physical(warehouse)).isEqualTo("2.000");
    assertThatThrownBy(() ->
            jdbc.update(
                "UPDATE inventory.stock_movements SET quantity=1 WHERE company_id=?", company))
        .isInstanceOf(DataAccessException.class);
    assertThatThrownBy(
            () -> jdbc.update("DELETE FROM inventory.stock_movements WHERE company_id=?", company))
        .isInstanceOf(DataAccessException.class);
    assertThatThrownBy(() ->
            jdbc.update(
                "UPDATE inventory.stock_positions SET reserved=3 WHERE company_id=?", company))
        .isInstanceOf(DataAccessException.class);
    assertThatThrownBy(() ->
            jdbc.update(
                "UPDATE inventory.stock_positions SET company_id=? WHERE company_id=?",
                other,
                company))
        .isInstanceOf(DataAccessException.class);
    jdbc.update("UPDATE inventory.stock_positions SET on_hand=3 WHERE company_id=?", company);
    assertThat(reconcile.check(new StockKey(company, item, warehouse)).orElseThrow().difference())
        .isEqualTo("1.000");
    assertThat(physical(warehouse)).isEqualTo("3.000");
  }
}
