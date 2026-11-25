package com.sanigest.catalog.api.dto;

import com.sanigest.catalog.domain.enums.SupplierPaymentTerms;
import com.sanigest.catalog.domain.enums.SupplierStatus;
import com.sanigest.catalog.domain.enums.SupplierSupplyType;
import com.sanigest.catalog.domain.model.Supplier;
import java.util.UUID;

public record SupplierDetailResponse(
    UUID id,
    String name,
    String taxId,
    String contact,
    String phone,
    String email,
    SupplierStatus status,
    String address,
    SupplierSupplyType supplyType,
    SupplierPaymentTerms paymentTerms,
    UUID categoryId,
    Integer creditDays,
    String observations) {
  public static SupplierDetailResponse from(Supplier supplier) {
    var details = supplier.details();
    var profile = details.profile();
    return new SupplierDetailResponse(
        supplier.id(),
        details.name(),
        details.taxId(),
        details.contact(),
        details.phone(),
        details.email(),
        supplier.status(),
        profile.address(),
        profile.supplyType(),
        profile.paymentTerms(),
        profile.categoryId(),
        profile.creditDays(),
        profile.observations());
  }
}
