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

  public Supplier updateDetails(SupplierPatch patch) {
    return new Supplier(id, companyId, patch.applyTo(details), status);
  }

  public Supplier changeStatus(SupplierStatus nextStatus) {
    return status == nextStatus ? this : new Supplier(id, companyId, details, nextStatus);
  }

  public static Supplier register(UUID companyId, SupplierDetails details) {
    var normalized =
        new SupplierDetails(
            details.name(),
            SupplierProfile.normalizeTaxId(
                details.taxId(), !details.profile().equals(SupplierProfile.EMPTY)),
            details.contact(),
            details.phone(),
            details.email(),
            details.profile());
    return new Supplier(UUID.randomUUID(), companyId, normalized, SupplierStatus.ACTIVE);
  }
}
