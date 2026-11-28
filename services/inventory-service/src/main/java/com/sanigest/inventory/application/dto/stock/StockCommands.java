package com.sanigest.inventory.application.dto.stock;

import com.sanigest.inventory.domain.model.stock.StockConfiguration;
import com.sanigest.inventory.domain.model.stock.StockMovement;
import java.math.BigDecimal;

public final class StockCommands {
  private StockCommands() {}

  public record Configure(
      StockConfiguration configuration, BigDecimal initialQuantity, Long revision) {}

  public record Adjust(StockMovement.Direction direction, BigDecimal quantity, String reason) {}
}
