package com.sanigest.catalog.api.dto;

import com.sanigest.catalog.domain.enums.SupplierStatus;
import com.sanigest.catalog.domain.model.Supplier;
import java.util.UUID;

public record SupplierDetailResponse(
    UUID id,
    String name,
    String taxId,
    String contact,
    String phone,
    String email,
    SupplierStatus status) {
  public static SupplierDetailResponse from(Supplier supplier) {
    var details = supplier.details();
    return new SupplierDetailResponse(
        supplier.id(),
        details.name(),
        details.taxId(),
        details.contact(),
        details.phone(),
        details.email(),
        supplier.status());
  }
}
