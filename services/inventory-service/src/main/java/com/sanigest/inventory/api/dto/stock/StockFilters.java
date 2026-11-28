package com.sanigest.inventory.api.dto.stock;

import com.sanigest.inventory.application.dto.InventoryQueries;
import com.sanigest.inventory.application.dto.InventoryQueries.Order;
import com.sanigest.inventory.application.dto.InventoryQueries.Paging;
import com.sanigest.inventory.application.dto.InventoryQueries.Sort;
import com.sanigest.inventory.domain.exception.InventoryViolation;
import com.sanigest.inventory.domain.model.stock.StockAvailability;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

public record StockFilters(
    UUID warehouseId,
    UUID catalogItemId,
    String availability,
    Boolean trackingEnabled,
    @Schema(description = "Busca por nombre o SKU en las ubicaciones seleccionadas.") String search,
    Integer page,
    Integer size,
    Sort sort,
    Order order,
    @Schema(description = "Hasta 100 UUID. Requiere warehouseId y excluye catalogItemId.")
        List<UUID> catalogItemIds) {
  public InventoryQueries.Stock query() {
    return new InventoryQueries.Stock(
        warehouseId,
        catalogItemId,
        availabilityFilter(),
        trackingEnabled,
        search,
        paging(page, size, sort, Sort.UPDATED_AT, order),
        catalogItemIds);
  }

  private StockAvailability availabilityFilter() {
    if (availability == null || availability.isBlank() || "ALL".equals(availability)) {
      return null;
    }
    try {
      return StockAvailability.valueOf(availability.strip());
    } catch (IllegalArgumentException invalid) {
      throw InventoryViolation.invalid("INVALID_REQUEST");
    }
  }

  static Paging paging(Integer page, Integer size, Sort sort, Sort fallback, Order order) {
    return new Paging(
        page == null ? 0 : page,
        size == null ? 20 : size,
        sort == null ? fallback : sort,
        order == null ? Order.DESC : order);
  }
}
