package com.sanigest.catalog.domain.model;

import com.sanigest.catalog.domain.enums.CatalogItemType;
import com.sanigest.catalog.domain.exception.InvalidCatalogItem;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

public record CatalogItem(
    UUID id, UUID companyId, String name, CatalogItemType type, BigDecimal price, boolean active) {
  public static final int NAME_LIMIT = 200;
  public static final int PRICE_PRECISION = 14;
  public static final int PRICE_SCALE = 2;
  public static final String CURRENCY = "DOP";

  private static final BigDecimal PRICE_LIMIT = BigDecimal.TEN.pow(PRICE_PRECISION - PRICE_SCALE);

  public CatalogItem {
    if (id == null || companyId == null || type == null) {
      throw new InvalidCatalogItem("Item ID, company and type are required");
    }
    if (name == null || name.isBlank() || name.length() > NAME_LIMIT || name.indexOf('\0') >= 0) {
      throw new InvalidCatalogItem("Invalid item name");
    }
    name = name.strip();
    price = normalizedPrice(price);
  }

  public static CatalogItem register(
      UUID companyId, String name, CatalogItemType type, BigDecimal price) {
    return new CatalogItem(UUID.randomUUID(), companyId, name, type, price, true);
  }

  public String currency() {
    return CURRENCY;
  }

  private static BigDecimal normalizedPrice(BigDecimal price) {
    if (price == null || price.signum() < 0 || price.compareTo(PRICE_LIMIT) >= 0) {
      throw new InvalidCatalogItem("Price is required and must be within the supported range");
    }
    if (price.stripTrailingZeros().scale() > PRICE_SCALE) {
      throw new InvalidCatalogItem("Price has more than two significant decimal places");
    }
    return price.setScale(PRICE_SCALE, RoundingMode.UNNECESSARY);
  }
}
