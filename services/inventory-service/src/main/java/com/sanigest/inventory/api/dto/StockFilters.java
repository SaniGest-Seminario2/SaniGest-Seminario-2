package com.sanigest.inventory.api.dto;

import com.sanigest.inventory.application.dto.InventoryQueries;
import com.sanigest.inventory.application.dto.InventoryQueries.Order;
import com.sanigest.inventory.application.dto.InventoryQueries.Paging;
import com.sanigest.inventory.application.dto.InventoryQueries.Sort;
import com.sanigest.inventory.domain.StockAvailability;
import java.util.List;
import java.util.UUID;

public record StockFilters(
    UUID warehouseId,
    UUID catalogItemId,
    StockAvailability availability,
    Boolean trackingEnabled,
    String search,
    Integer page,
    Integer size,
    Sort sort,
    Order order,
    List<UUID> catalogItemIds) {
  public InventoryQueries.Stock query() {
    return new InventoryQueries.Stock(
        warehouseId,
        catalogItemId,
        availability,
        trackingEnabled,
        search,
        paging(page, size, sort, Sort.UPDATED_AT, order),
        catalogItemIds);
  }

  static Paging paging(Integer page, Integer size, Sort sort, Sort fallback, Order order) {
    return new Paging(
        page == null ? 0 : page,
        size == null ? 20 : size,
        sort == null ? fallback : sort,
        order == null ? Order.DESC : order);
  }
}
