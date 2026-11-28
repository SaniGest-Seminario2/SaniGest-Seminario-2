package com.sanigest.catalog.domain.model.catalogitem;

import com.sanigest.catalog.domain.exception.catalogitem.InvalidCatalogItem;
import java.util.UUID;

public record CatalogItemCategory(
    UUID id, UUID companyId, String code, String name, boolean active) {
  public CatalogItemCategory {
    if (!code.matches("[A-Z][A-Z0-9_]{0,31}")
        || name == null
        || name.isBlank()
        || name.length() > 100
        || name.indexOf(0) >= 0) {
      throw new InvalidCatalogItem("Invalid item category");
    }
    name = name.strip();
  }
}
