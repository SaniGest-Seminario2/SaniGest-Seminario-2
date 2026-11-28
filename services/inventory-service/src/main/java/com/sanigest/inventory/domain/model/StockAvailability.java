package com.sanigest.inventory.domain.model;

public enum StockAvailability {
  NOT_TRACKED,
  OUT,
  LOW,
  AVAILABLE,
  NOT_APPLICABLE;

  public static StockAvailability derive(StockConfiguration configuration, StockBalance balance) {
    if (!configuration.trackingEnabled()) {
      return NOT_TRACKED;
    }
    if (balance.available().signum() == 0) {
      return OUT;
    }
    return balance.available().compareTo(configuration.minimumStock()) <= 0 ? LOW : AVAILABLE;
  }
}
