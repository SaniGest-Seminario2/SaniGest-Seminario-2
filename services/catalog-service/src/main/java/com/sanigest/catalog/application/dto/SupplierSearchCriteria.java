package com.sanigest.catalog.application.dto;

import com.sanigest.catalog.domain.enums.SupplierPaymentTerms;
import com.sanigest.catalog.domain.enums.SupplierStatus;
import com.sanigest.catalog.domain.enums.SupplierSupplyType;
import java.util.UUID;

public record SupplierSearchCriteria(
    String search,
    SupplierStatus status,
    SupplierSupplyType supplyType,
    SupplierPaymentTerms paymentTerms,
    UUID categoryId,
    boolean descending) {}
