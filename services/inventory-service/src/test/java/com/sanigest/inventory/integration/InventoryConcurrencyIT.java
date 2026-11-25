package com.sanigest.inventory.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@Tag("regression")
class InventoryConcurrencyIT extends InventoryHttpFixture {
  @Test
  void concurrentWarehouseCreationAndInitializationHaveOneWinner() throws Exception {
    var body = "{\"code\":\"MAIN\",\"name\":\"Main\"}";
    var pair =
        race(
            () -> send("POST", WAREHOUSES, company, null, body, token),
            () -> send("POST", WAREHOUSES, company, null, body, token));
    assertStatuses(pair, 201, 409);
    var warehouse =
        UUID.fromString(
            json.readTree(
                    pair.stream()
                        .filter(reply -> reply.statusCode() == 201)
                        .findFirst()
                        .orElseThrow()
                        .body())
                .get("id")
                .asString());
    var settings = config(true, "10");
    assertStatuses(
        race(
            () -> send("PUT", position(warehouse), company, "start-a", settings, token),
            () -> send("PUT", position(warehouse), company, "start-b", settings, token)),
        200,
        409);
    assertThat(physical(warehouse)).isEqualTo("10.000");
    assertThat(countMovements(warehouse)).isEqualTo(1);
  }

  @ParameterizedTest
  @CsvSource({"IN,3,201,16.000,3", "OUT,3,201,4.000,3", "OUT,7,409,3.000,2"})
  void serializesConcurrentAdjustments(
      String direction, String amount, int secondStatus, String balance, long movements)
      throws Exception {
    var warehouse = warehouse();
    initialize(warehouse, "10");
    var body = adjustment(warehouse, direction, amount);
    assertStatuses(
        race(
            () -> send("POST", ADJUST, company, "first", body, token),
            () -> send("POST", ADJUST, company, "second", body, token)),
        201,
        secondStatus);
    assertThat(physical(warehouse)).isEqualTo(balance);
    assertThat(countMovements(warehouse)).isEqualTo(movements);
  }

  @ParameterizedTest
  @CsvSource({"2,201", "3,409"})
  void resolvesCompetingIdempotencyRequests(String secondQuantity, int status) throws Exception {
    var warehouse = warehouse();
    initialize(warehouse, "10");
    var pair =
        race(
            () -> send("POST", ADJUST, company, "shared", adjustment(warehouse, "IN", "2"), token),
            () ->
                send(
                    "POST",
                    ADJUST,
                    company,
                    "shared",
                    adjustment(warehouse, "IN", secondQuantity),
                    token));
    assertStatuses(pair, 201, status);
    if (status == 201) {
      assertThat(json.readTree(pair.getFirst().body()))
          .isEqualTo(json.readTree(pair.getLast().body()));
    }
    assertThat(physical(warehouse).toPlainString()).isIn("12.000", "13.000");
    assertThat(countMovements(warehouse)).isEqualTo(2);
  }

  @ParameterizedTest
  @CsvSource({"warehouse", "tracking"})
  void serializesAdjustmentWithClosure(String mode) throws Exception {
    var warehouse = warehouse();
    initialize(warehouse, null);
    Callable<HttpResponse<String>> close =
        "warehouse".equals(mode)
            ? () ->
                send(
                    "PATCH",
                    WAREHOUSES + "/" + warehouse,
                    company,
                    null,
                    "{\"status\":\"INACTIVE\"}",
                    token)
            : () -> send("PUT", position(warehouse), company, null, config(false, null), token);
    var pair =
        race(
            close,
            () -> send("POST", ADJUST, company, "race", adjustment(warehouse, "IN", "1"), token));
    if (pair.getFirst().statusCode() == 200) {
      assertStatuses(pair, 200, 409);
      assertThat(physical(warehouse)).isEqualTo("0.000");
      assertThat(countMovements(warehouse)).isZero();
    } else {
      assertStatuses(pair, 409, 201);
      assertThat(physical(warehouse)).isEqualTo("1.000");
      assertThat(countMovements(warehouse)).isEqualTo(1);
    }
  }

  @Test
  void rejectsStaleRevisionsAndAllowsAdjustmentsInSeparateWarehouses() throws Exception {
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
    initialize(first, null);
    expect("PUT", position(second), company, null, config(true, null), 200);
    String update = "{\"trackingEnabled\":true,\"minimumStock\":\"1\",\"revision\":0}";
    assertStatuses(
        race(
            () -> send("PUT", position(first), company, null, update, token),
            () -> send("PUT", position(first), company, null, update, token)),
        200,
        409);
    assertStatuses(
        race(
            () -> send("POST", ADJUST, company, "first", adjustment(first, "IN", "1"), token),
            () -> send("POST", ADJUST, company, "second", adjustment(second, "IN", "2"), token)),
        201,
        201);
    assertThat(physical(first)).isEqualTo("1.000");
    assertThat(physical(second)).isEqualTo("2.000");
  }

  private static void assertStatuses(List<HttpResponse<String>> responses, int first, int second) {
    assertThat(responses)
        .extracting(HttpResponse::statusCode)
        .containsExactlyInAnyOrder(first, second);
  }

  private static List<HttpResponse<String>> race(
      Callable<HttpResponse<String>> first, Callable<HttpResponse<String>> second)
      throws Exception {
    var gate = new CyclicBarrier(2);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var left =
          pool.submit(() -> {
            gate.await(5, TimeUnit.SECONDS);
            return first.call();
          });
      var right =
          pool.submit(() -> {
            gate.await(5, TimeUnit.SECONDS);
            return second.call();
          });
      return List.of(left.get(15, TimeUnit.SECONDS), right.get(15, TimeUnit.SECONDS));
    }
  }
}
