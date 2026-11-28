package com.sanigest.catalog.domain.model.catalogitem;

import com.sanigest.catalog.domain.enums.catalogitem.CatalogItemType;
import com.sanigest.catalog.domain.exception.catalogitem.InvalidCatalogItem;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;

public record CatalogItem(
    UUID id,
    UUID companyId,
    String name,
    CatalogItemType type,
    BigDecimal price,
    boolean active,
    String description,
    CatalogItemProfile profile) {
  public static final int NAME_LIMIT = 200;
  public static final int DESCRIPTION_LIMIT = 1000;
  public static final int PRICE_PRECISION = 19;
  public static final int PRICE_SCALE = 4;
  public static final String CURRENCY = "DOP";

  public CatalogItem {
    if (id == null || companyId == null || type == null || profile == null) {
      throw new InvalidCatalogItem("Item ID, company, type and profile are required");
    }
    if (name == null || name.isBlank() || name.length() > NAME_LIMIT || name.indexOf('\0') >= 0) {
      throw new InvalidCatalogItem("Invalid item name");
    }
    name = name.strip();
    price = CatalogPricing.unitAmount(price);
    price =
        price.setScale(Math.max(2, price.stripTrailingZeros().scale()), RoundingMode.UNNECESSARY);
    if (description != null
        && (description.length() > DESCRIPTION_LIMIT || description.indexOf(0) >= 0)) {
      throw new InvalidCatalogItem("Invalid item description");
    }
  }

  public CatalogItem(
      UUID id,
      UUID companyId,
      String name,
      CatalogItemType type,
      BigDecimal price,
      boolean active) {
    this(id, companyId, name, type, price, active, null);
  }

  public CatalogItem(
      UUID id,
      UUID companyId,
      String name,
      CatalogItemType type,
      BigDecimal price,
      boolean active,
      String description) {
    this(id, companyId, name, type, price, active, description, CatalogItemProfile.LEGACY);
  }

  public CatalogItem apply(CatalogItemPatch patch) {
    return new CatalogItem(
        id,
        companyId,
        patch.name().orElse(name),
        type,
        patch.price().orElse(price),
        active,
        patch.description().orElse(Optional.ofNullable(description)).orElse(null),
        patch.applyTo(profile));
  }

  public CatalogItem withActive(boolean newActive) {
    return newActive == active
        ? this
        : new CatalogItem(id, companyId, name, type, price, newActive, description, profile);
  }

  public static CatalogItem register(
      UUID companyId, String name, CatalogItemType type, BigDecimal price) {
    return new CatalogItem(UUID.randomUUID(), companyId, name, type, price, true);
  }

  public String currency() {
    return CURRENCY;
  }
}
