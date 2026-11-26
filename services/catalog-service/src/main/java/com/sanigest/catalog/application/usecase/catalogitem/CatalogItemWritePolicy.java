package com.sanigest.catalog.application.usecase.catalogitem;

import com.sanigest.catalog.application.port.out.persistence.CatalogItemCommercialRepository;
import com.sanigest.catalog.domain.exception.InvalidCatalogItem;
import com.sanigest.catalog.domain.model.CatalogItem;
import com.sanigest.catalog.domain.model.CatalogItemProfile;
import java.math.BigDecimal;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CatalogItemWritePolicy {
  private static final BigDecimal LEGACY_LIMIT = BigDecimal.TEN.pow(12);
  private final CatalogItemCommercialRepository references;
  private final CatalogBusinessDate date;
  private final boolean extendedPrices;

  CatalogItemWritePolicy(
      CatalogItemCommercialRepository references,
      CatalogBusinessDate date,
      @Value("${sanigest.catalog.extended-prices-enabled:false}") boolean extendedPrices) {
    this.references = references;
    this.date = date;
    this.extendedPrices = extendedPrices;
  }

  void validate(CatalogItem previous, CatalogItem current) {
    var profile = current.profile();
    var before = previous == null ? CatalogItemProfile.LEGACY : previous.profile();
    if (previous == null
        && (profile.unitCode() == null
            || profile.taxClassificationCode() == null
            || profile.availableForPurchase() == null)) {
      throw new InvalidCatalogItem(
          "Unit, tax classification and purchase availability are required");
    }
    validatePrice(previous, current);
    validateReferences(current, before, profile);
  }

  private void validatePrice(CatalogItem previous, CatalogItem current) {
    if ((previous == null || !previous.price().equals(current.price()))
        && !extendedPrices
        && (current.price().compareTo(LEGACY_LIMIT) >= 0
            || current.price().stripTrailingZeros().scale() > 2)) {
      throw new InvalidCatalogItem("Price exceeds the current Sales compatibility limit");
    }
  }

  private void validateReferences(
      CatalogItem current, CatalogItemProfile before, CatalogItemProfile profile) {
    if (profile.categoryId() != null
        && !Objects.equals(profile.categoryId(), before.categoryId())
        && !references.lockCategory(current.companyId(), profile.categoryId()).active()) {
      throw new InvalidCatalogItem("Inactive item category");
    }
    if (profile.unitCode() != null
        && !Objects.equals(profile.unitCode(), before.unitCode())
        && !references.activeUnit(profile.unitCode())) {
      throw new InvalidCatalogItem("Invalid unit of measure");
    }
    if (profile.taxClassificationCode() != null
        && !Objects.equals(profile.taxClassificationCode(), before.taxClassificationCode())
        && !references.currentTax(profile.taxClassificationCode(), date.today())) {
      throw new InvalidCatalogItem("Invalid tax classification");
    }
  }
}
