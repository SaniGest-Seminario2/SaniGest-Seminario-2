package com.sanigest.sales.infrastructure.integration.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record CatalogItemResponse(
    String id,
    String companyId,
    String name,
    String type,
    @JsonProperty(required = true) String description,
    BigDecimal price,
    String currency,
    Boolean active,
    Boolean availableForSale,
    String taxRate) {}
