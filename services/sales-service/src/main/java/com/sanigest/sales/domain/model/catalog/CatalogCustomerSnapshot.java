package com.sanigest.sales.domain.model.catalog;

import java.util.UUID;

public record CatalogCustomerSnapshot(UUID id, String name, Status status) {
  public enum Status {
    ACTIVE,
    INACTIVE
  }
}
