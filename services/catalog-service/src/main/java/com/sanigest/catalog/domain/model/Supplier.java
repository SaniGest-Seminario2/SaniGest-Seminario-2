package com.sanigest.catalog.domain.model;

import com.sanigest.catalog.domain.enums.SupplierStatus;
import java.util.Objects;
import java.util.UUID;

public record Supplier(UUID id, UUID companyId, SupplierDetails details, SupplierStatus status) {
  public Supplier {
    Objects.requireNonNull(id);
    Objects.requireNonNull(companyId);
    Objects.requireNonNull(details);
    Objects.requireNonNull(status);
  }

  public static Supplier register(UUID companyId, SupplierDetails details) {
    return new Supplier(UUID.randomUUID(), companyId, details, SupplierStatus.ACTIVE);
  }
}
