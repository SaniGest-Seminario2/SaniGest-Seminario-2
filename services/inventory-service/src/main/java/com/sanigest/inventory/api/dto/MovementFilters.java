package com.sanigest.inventory.api.dto;

import com.sanigest.inventory.application.dto.InventoryQueries;
import com.sanigest.inventory.application.dto.InventoryQueries.Order;
import com.sanigest.inventory.application.dto.InventoryQueries.Sort;
import com.sanigest.inventory.domain.StockMovement;
import java.time.Instant;
import java.util.UUID;

public record MovementFilters(
    UUID warehouseId,
    UUID catalogItemId,
    StockMovement.Type movementType,
    StockMovement.Direction direction,
    StockMovement.Origin originType,
    UUID originId,
    Instant dateFrom,
    Instant dateTo,
    Integer page,
    Integer size,
    Sort sort,
    Order order) {
  public InventoryQueries.Movements query() {
    return new InventoryQueries.Movements(
        warehouseId,
        catalogItemId,
        movementType,
        direction,
        originType,
        originId,
        dateFrom,
        dateTo,
        StockFilters.paging(page, size, sort, Sort.OCCURRED_AT, order));
  }
}
