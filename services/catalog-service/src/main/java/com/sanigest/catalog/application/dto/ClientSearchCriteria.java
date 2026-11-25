package com.sanigest.catalog.application.dto;

import com.sanigest.catalog.domain.enums.ClientPaymentTerms;
import com.sanigest.catalog.domain.enums.ClientType;
import java.util.UUID;

public record ClientSearchCriteria(
    String search,
    String status,
    ClientType clientType,
    UUID categoryId,
    ClientPaymentTerms paymentTerms,
    boolean descending) {}
