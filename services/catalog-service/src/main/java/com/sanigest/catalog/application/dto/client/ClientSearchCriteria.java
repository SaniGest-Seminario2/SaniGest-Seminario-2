package com.sanigest.catalog.application.dto.client;

import com.sanigest.catalog.domain.enums.client.ClientPaymentTerms;
import com.sanigest.catalog.domain.enums.client.ClientType;
import java.util.UUID;

public record ClientSearchCriteria(
    String search,
    String status,
    ClientType clientType,
    UUID categoryId,
    ClientPaymentTerms paymentTerms,
    boolean descending) {}
