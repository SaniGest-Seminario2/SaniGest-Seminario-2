package com.sanigest.inventory.domain.model.stock;

import com.sanigest.inventory.domain.exception.InventoryViolation;
import java.math.BigDecimal;

public record StockBalance(BigDecimal onHand, BigDecimal reserved) {
  public StockBalance {
    onHand = Quantity.validate(onHand, true, false);
    reserved = Quantity.validate(reserved, true, false);
    if (reserved.compareTo(onHand) > 0) {
      throw InventoryViolation.conflict("INSUFFICIENT_STOCK");
    }
  }

  public BigDecimal available() {
    return onHand.subtract(reserved);
  }

  public StockBalance apply(StockMovement.Direction direction, BigDecimal quantity) {
    var amount = Quantity.validate(quantity, true, true);
    BigDecimal next =
        switch (direction) {
          case IN -> onHand.add(amount);
          case OUT -> onHand.subtract(amount);
        };
    if (next.compareTo(reserved) < 0) {
      throw InventoryViolation.conflict("INSUFFICIENT_STOCK");
    }
    return new StockBalance(next, reserved);
  }

  public boolean empty() {
    return onHand.signum() == 0 && reserved.signum() == 0;
  }
}
