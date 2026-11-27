package com.sanigest.sales.application.dto;

import com.sanigest.sales.domain.enums.ReceivableStatus;

public record ReceivableSearch(String search, Filter status) {
  public enum Filter {
    ALL(null),
    PENDING(ReceivableStatus.OPEN),
    PARTIALLY_PAID(ReceivableStatus.PARTIALLY_PAID);

    private final ReceivableStatus receivableStatus;

    Filter(ReceivableStatus receivableStatus) {
      this.receivableStatus = receivableStatus;
    }

    public ReceivableStatus receivableStatus() {
      return receivableStatus;
    }
  }
}
