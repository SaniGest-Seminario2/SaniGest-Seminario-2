package com.sanigest.catalog.api.dto;

import com.sanigest.catalog.domain.enums.SupplierStatus;
import com.sanigest.catalog.domain.model.Supplier;
import java.util.UUID;

public record SupplierResponse(
    UUID id,
    UUID companyId,
    String name,
    String taxId,
    String contact,
    String phone,
    String email,
    SupplierStatus status) {

  public static SupplierResponse from(Supplier supplier) {
    var details = supplier.details();
    return new SupplierResponse(
        supplier.id(),
        supplier.companyId(),
        details.name(),
        details.taxId(),
        details.contact(),
        details.phone(),
        details.email(),
        supplier.status());
  }
}
