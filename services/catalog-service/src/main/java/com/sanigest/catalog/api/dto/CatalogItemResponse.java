package com.sanigest.catalog.api.dto;

import com.sanigest.catalog.application.dto.CatalogItemCommercialData;
import com.sanigest.catalog.domain.enums.CatalogItemType;
import com.sanigest.catalog.domain.model.CatalogItem;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CatalogItemResponse(
    UUID id,
    UUID companyId,
    String name,
    CatalogItemType type,
    BigDecimal price,
    String currency,
    boolean active,
    String description,
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
  public static CatalogItemResponse from(CatalogItem item) {
    return from(item, CatalogItemCommercialData.EMPTY);
  }

  public CatalogItemResponse(
      UUID id,
      UUID companyId,
      String name,
      CatalogItemType type,
      BigDecimal price,
      String currency,
      boolean active) {
    this(
        id, companyId, name, type, price, currency, active, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, true, null, active, false, null, null,
        null);
  }

  public static CatalogItemResponse from(CatalogItem item, CatalogItemCommercialData data) {
    var detail = CatalogItemDetailResponse.from(item, data);
    return new CatalogItemResponse(
        item.id(),
        item.companyId(),
        item.name(),
        item.type(),
        item.price(),
        item.currency(),
        item.active(),
        item.description(),
        detail.sku(),
        detail.barcode(),
        detail.categoryId(),
        detail.categoryName(),
        detail.unitCode(),
        detail.estimatedCost(),
        detail.taxClassificationCode(),
        detail.taxRate(),
        detail.taxEffectiveFrom(),
        detail.taxEffectiveUntil(),
        detail.unitTaxAmount(),
        detail.finalPrice(),
        detail.grossProfit(),
        detail.grossMargin(),
        detail.availableForSale(),
        detail.availableForPurchase(),
        detail.eligibleForSale(),
        detail.eligibleForPurchase(),
        detail.primarySupplierId(),
        detail.unitDivisible(),
        detail.primarySupplierName());
  }
}
