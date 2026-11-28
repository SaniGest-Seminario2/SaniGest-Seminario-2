package com.sanigest.catalog.application.dto.supplier;

import com.sanigest.catalog.domain.enums.supplier.SupplierPaymentTerms;
import com.sanigest.catalog.domain.enums.supplier.SupplierStatus;
import com.sanigest.catalog.domain.enums.supplier.SupplierSupplyType;
import java.util.UUID;

public record SupplierSearchCriteria(
    String search,
    SupplierStatus status,
    SupplierSupplyType supplyType,
    SupplierPaymentTerms paymentTerms,
    UUID categoryId,
    boolean descending) {}
