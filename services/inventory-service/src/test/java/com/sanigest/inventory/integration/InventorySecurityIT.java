package com.sanigest.inventory.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("regression")
class InventorySecurityIT extends InventoryHttpFixture {
  @Test
  void scopesWarehouseStockHistorySummaryAndReplayByCompany() throws Exception {
    var warehouse = warehouse();
    initialize(warehouse, "3");
    expect("POST", ADJUST, company, "shared", adjustment(warehouse, "IN", "1"), 201);
    expect("POST", WAREHOUSES, other, null, "{\"code\":\"ALM-1\",\"name\":\"Other\"}", 403);
    assertThat(expect("GET", WAREHOUSES, other, null, null, 200).get("totalElements").asInt())
        .isZero();
    assertThat(expect("GET", STOCK, other, null, null, 200).get("totalElements").asInt()).isZero();
    assertThat(expect("GET", MOVEMENTS, other, null, null, 200).get("totalElements").asInt())
        .isZero();
    assertThat(expect("GET", SUMMARY, other, null, null, 200).get("activeWarehouses").asInt())
        .isZero();
    expect("GET", WAREHOUSES + "/" + warehouse, other, null, null, 404);
    membership(other, "ADMIN", false);
    var second =
        UUID.fromString(
            expect("POST", WAREHOUSES, other, null, "{\"code\":\"ALM-1\",\"name\":\"Other\"}", 201)
                .get("id")
                .asString());
    expect("GET", position(second), other, null, null, 404);
    catalogFailure(item, 404);
    expect("PUT", position(second), other, "initial", config(true, "3"), 404);
    catalog(other, item, "PRODUCT", true, "UNIT", false);
    expect("PUT", position(second), other, "initial", config(true, "3"), 200);
    expect("POST", ADJUST, other, "shared", adjustment(second, "IN", "2"), 201);
    assertThat(physical(warehouse)).isEqualTo("4.000");
    revoke(company);
    expect("POST", ADJUST, company, "shared", adjustment(warehouse, "IN", "1"), 403);
  }

  @Test
  void warehouseOptionsFilterBeforeSelectingAndKeepTheCompanyBoundary() throws Exception {
    var warehouse = warehouse();
    var inactive =
        expect(
                "POST",
                WAREHOUSES,
                company,
                null,
                "{\"code\":\"ALM-2\",\"name\":\"Warehouse inactive\"}",
                201)
            .path("id")
            .asString();
    expect("PATCH", WAREHOUSES + "/" + inactive, company, null, "{\"status\":\"INACTIVE\"}", 200);

    var path = WAREHOUSES + "/options?search=ware";
    var options = expect("GET", path, company, null, null, 200);
    assertThat(options.size()).isEqualTo(1);
    assertThat(options.get(0).size()).isEqualTo(2);
    assertThat(options.get(0).path("id").asString()).isEqualTo(warehouse.toString());
    assertThat(options.get(0).path("name").asString()).isEqualTo("Warehouse");
    assertThat(expect("GET", path, other, null, null, 200).size()).isZero();
    assertThat(
            expect("GET", WAREHOUSES + "/options?search=missing", company, null, null, 200).size())
        .isZero();
    initialize(warehouse, "3");
    assertThat(
            expect("GET", STOCK + "?availability=ALL", company, null, null, 200)
                .path("totalElements")
                .asInt())
        .isEqualTo(1);
  }

  @Test
  void doesNotTreatOperatorWriteGrantAsAnAdjustmentPermission() throws Exception {
    var warehouse = warehouse();
    initialize(warehouse, "3");
    membership(company, "OPERATOR", false);
    expect("GET", STOCK, company, null, null, 403);
    membership(company, "OPERATOR", true);
    expect("GET", STOCK, company, null, null, 200);
    expect("POST", ADJUST, company, "denied", adjustment(warehouse, "IN", "1"), 403);
    assertThat(physical(warehouse)).isEqualTo("3.000");
  }

  @Test
  void rejectsInvalidJwtAndUnavailableReferencesBeforeEffects() throws Exception {
    var warehouse = warehouse();
    assertThat(
            send("GET", STOCK, company, null, null, token(actor, SERVICE_KEYS, "sanigest-api-test"))
                .statusCode())
        .isEqualTo(401);
    assertThat(
            send("GET", STOCK, company, null, null, token(actor, USER_KEYS, "wrong-audience"))
                .statusCode())
        .isEqualTo(401);
    catalogFailure(item, 503);
    expect("PUT", position(warehouse), company, "initial", config(true, "1"), 503);
    assertThat(countMovements(warehouse)).isZero();
  }
}
