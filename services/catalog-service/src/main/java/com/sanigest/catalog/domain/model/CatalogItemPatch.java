package com.sanigest.catalog.domain.model;

import com.sanigest.catalog.domain.exception.InvalidCatalogItem;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public record CatalogItemPatch(
    Optional<String> name,
    Optional<Optional<String>> description,
    Optional<BigDecimal> price,
    Optional<Optional<String>> sku,
    Optional<Optional<String>> barcode,
    Optional<Optional<UUID>> categoryId,
    Optional<Optional<String>> unitCode,
    Optional<Optional<BigDecimal>> estimatedCost,
    Optional<Optional<String>> taxClassificationCode,
    Optional<Boolean> availableForSale,
    Optional<Boolean> availableForPurchase) {
  public CatalogItemPatch {
    if (name.isEmpty()
        && description.isEmpty()
        && price.isEmpty()
        && sku.isEmpty()
        && barcode.isEmpty()
        && categoryId.isEmpty()
        && unitCode.isEmpty()
        && estimatedCost.isEmpty()
        && taxClassificationCode.isEmpty()
        && availableForSale.isEmpty()
        && availableForPurchase.isEmpty()) {
      throw new InvalidCatalogItem("At least one editable field is required");
    }
  }

  public CatalogItemPatch(
      Optional<String> name, Optional<Optional<String>> description, Optional<BigDecimal> price) {
    this(
        name,
        description,
        price,
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }

  CatalogItemProfile applyTo(CatalogItemProfile profile) {
    return new CatalogItemProfile(
        sku.orElse(Optional.ofNullable(profile.sku())).orElse(null),
        barcode.orElse(Optional.ofNullable(profile.barcode())).orElse(null),
        categoryId.orElse(Optional.ofNullable(profile.categoryId())).orElse(null),
        unitCode.orElse(Optional.ofNullable(profile.unitCode())).orElse(null),
        estimatedCost.orElse(Optional.ofNullable(profile.estimatedCost())).orElse(null),
        taxClassificationCode
            .orElse(Optional.ofNullable(profile.taxClassificationCode()))
            .orElse(null),
        availableForSale.orElse(profile.availableForSale()),
        availableForPurchase.orElse(profile.availableForPurchase()));
  }
}
