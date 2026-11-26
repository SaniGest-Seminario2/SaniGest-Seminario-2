package com.sanigest.inventory.api.dto;

import static com.sanigest.inventory.api.dto.ValueOption.of;

import com.sanigest.inventory.domain.StockAvailability;
import java.util.List;

public record InventoryOptions(
    List<ValueOption<String>> availabilityFilters, List<ValueOption<String>> availabilityStates) {
  public static InventoryOptions defaults() {
    var available = of(StockAvailability.AVAILABLE, "Disponible");
    var low = of(StockAvailability.LOW, "Stock bajo");
    var out = of(StockAvailability.OUT, "Agotado");
    var untracked = of(StockAvailability.NOT_TRACKED, "Sin seguimiento");
    return new InventoryOptions(
        List.of(new ValueOption<>("ALL", "Todos"), available, low, out, untracked),
        List.of(available, low, out, untracked, of(StockAvailability.NOT_APPLICABLE, "No aplica")));
  }
}
