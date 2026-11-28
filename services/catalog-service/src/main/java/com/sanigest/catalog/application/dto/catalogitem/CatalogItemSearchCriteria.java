package com.sanigest.catalog.application.dto.catalogitem;

import com.sanigest.catalog.domain.enums.catalogitem.CatalogItemType;
import java.util.UUID;

public record CatalogItemSearchCriteria(
    CatalogItemType type,
    String search,
    Boolean active,
    UUID categoryId,
    Boolean availableForSale,
    Boolean availableForPurchase) {}
