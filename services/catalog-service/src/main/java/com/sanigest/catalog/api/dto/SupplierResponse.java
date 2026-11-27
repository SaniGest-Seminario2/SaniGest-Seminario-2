package com.sanigest.catalog.api.dto;

import com.sanigest.catalog.domain.enums.SupplierPaymentTerms;
import com.sanigest.catalog.domain.enums.SupplierStatus;
import com.sanigest.catalog.domain.enums.SupplierSupplyType;
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
    SupplierStatus status,
    String address,
    SupplierSupplyCoverage supplyType,
    SupplierSupplyType supplyClassification,
    SupplierPaymentTerms paymentTerms,
    UUID categoryId,
    Integer creditDays,
    String observations) {

  public static SupplierResponse from(Supplier supplier) {
    var details = supplier.details();
    var profile = details.profile();
    return new SupplierResponse(
        supplier.id(),
        supplier.companyId(),
        details.name(),
        details.taxId(),
        details.contact(),
        details.phone(),
        details.email(),
        supplier.status(),
        profile.address(),
        SupplierSupplyCoverage.from(profile.supplyType()),
        profile.supplyType(),
        profile.paymentTerms(),
        profile.categoryId(),
        profile.creditDays(),
        profile.observations());
  }
}
