package com.sanigest.catalog.domain.model;

import com.sanigest.catalog.domain.exception.InvalidCatalogItem;
import java.math.BigDecimal;
import java.util.Optional;

/** A present but empty description value explicitly clears the stored description. */
public record CatalogItemPatch(
    Optional<String> name, Optional<Optional<String>> description, Optional<BigDecimal> price) {
  public CatalogItemPatch {
    if (name.isEmpty() && description.isEmpty() && price.isEmpty()) {
      throw new InvalidCatalogItem("At least one editable field is required");
    }
  }
}
