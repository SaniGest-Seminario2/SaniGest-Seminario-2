package com.sanigest.catalog.api.dto.catalogitem;

import com.sanigest.catalog.application.dto.catalogitem.CatalogItemCommercialData;
import com.sanigest.catalog.domain.enums.catalogitem.CatalogItemType;
import com.sanigest.catalog.domain.model.catalogitem.CatalogItem;
import com.sanigest.catalog.domain.model.catalogitem.CatalogPricing;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CatalogItemDetailResponse(
    UUID id,
    String name,
    CatalogItemType type,
    String description,
    BigDecimal price,
    String currency,
    boolean active,
    String sku,
    String barcode,
    UUID categoryId,
    String categoryName,
    String unitCode,
    String estimatedCost,
    String taxClassificationCode,
    String taxRate,
    LocalDate taxEffectiveFrom,
    LocalDate taxEffectiveUntil,
    String unitTaxAmount,
    String finalPrice,
    String grossProfit,
    String grossMargin,
    boolean availableForSale,
    Boolean availableForPurchase,
    boolean eligibleForSale,
    boolean eligibleForPurchase,
    UUID primarySupplierId,
    Boolean unitDivisible,
    String primarySupplierName) {
  public static CatalogItemDetailResponse from(CatalogItem item) {
    return from(item, CatalogItemCommercialData.EMPTY);
  }

  public static CatalogItemDetailResponse from(CatalogItem item, CatalogItemCommercialData data) {
    var profile = item.profile();
    var pricing = CatalogPricing.calculate(item.price(), profile.estimatedCost(), data.taxRate());
    return new CatalogItemDetailResponse(
        item.id(),
        item.name(),
        item.type(),
        item.description(),
        item.price(),
        item.currency(),
        item.active(),
        profile.sku(),
        profile.barcode(),
        profile.categoryId(),
        data.categoryName(),
        profile.unitCode(),
        decimal(profile.estimatedCost()),
        profile.taxClassificationCode(),
        decimal(data.taxRate()),
        data.taxEffectiveFrom(),
        data.taxEffectiveUntil(),
        decimal(pricing.unitTaxAmount()),
        decimal(pricing.finalPrice()),
        decimal(pricing.grossProfit()),
        decimal(pricing.grossMargin()),
        profile.availableForSale(),
        profile.availableForPurchase(),
        item.active() && profile.availableForSale(),
        item.active() && Boolean.TRUE.equals(profile.availableForPurchase()),
        data.primarySupplierId(),
        data.unitDivisible(),
        data.primarySupplierName());
  }

  public static String decimal(BigDecimal value) {
    return value == null ? null : value.toPlainString();
  }
}
