package com.sanigest.catalog.domain.model;

import com.sanigest.catalog.domain.exception.InvalidSupplierDetails;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record SupplierItemOffer(
    UUID catalogItemId, BigDecimal offeredCost, String supplierReference, boolean primary) {
  public SupplierItemOffer {
    Objects.requireNonNull(catalogItemId);
    if (offeredCost != null) {
      if (offeredCost.signum() < 0
          || offeredCost.scale() > 4
          || (long) offeredCost.precision() - offeredCost.scale() > 15) {
        throw new InvalidSupplierDetails("offeredCost");
      }
      offeredCost = offeredCost.setScale(4);
    }
    supplierReference = SupplierProfile.optionalText(supplierReference, 160, "supplierReference");
  }
}
