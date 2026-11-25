package com.sanigest.catalog.domain.model;

import com.sanigest.catalog.domain.exception.InvalidCatalogItem;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

public record CatalogItemProfile(
    String sku,
    String barcode,
    UUID categoryId,
    String unitCode,
    BigDecimal estimatedCost,
    String taxClassificationCode,
    boolean availableForSale,
    Boolean availableForPurchase) {

  public static final CatalogItemProfile LEGACY =
      new CatalogItemProfile(null, null, null, null, null, null, true, null);

  public CatalogItemProfile {
    sku = normalize(sku, 64);
    if (sku != null) {
      sku = normalize(sku.toUpperCase(Locale.ROOT), 64);
    }
    barcode = normalize(barcode, 64);
    unitCode = normalize(unitCode, 32);
    taxClassificationCode = normalize(taxClassificationCode, 32);
    if (estimatedCost != null) {
      estimatedCost = CatalogPricing.unitAmount(estimatedCost);
    }
  }

  private static String normalize(String value, int limit) {
    if (value == null) {
      return null;
    }
    var normalized = value.strip();
    if (normalized.isEmpty()
        || normalized.length() > limit
        || normalized.codePoints().anyMatch(Character::isISOControl)) {
      throw new InvalidCatalogItem("Invalid commercial code");
    }
    return normalized;
  }
}
