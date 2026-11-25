package com.sanigest.catalog.application.dto;

import com.sanigest.catalog.domain.enums.CatalogItemType;
import java.util.UUID;

public record CatalogItemSearchCriteria(
    CatalogItemType type,
    String search,
    Boolean active,
    UUID categoryId,
    Boolean availableForSale,
    Boolean availableForPurchase) {}
