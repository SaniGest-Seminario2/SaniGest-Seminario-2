package com.sanigest.catalog.domain.model;

import com.sanigest.catalog.domain.enums.CatalogItemType;
import com.sanigest.catalog.domain.enums.SupplierPaymentTerms;
import com.sanigest.catalog.domain.enums.SupplierSupplyType;
import com.sanigest.catalog.domain.exception.InvalidSupplierDetails;
import java.util.UUID;

public record SupplierProfile(
    String address,
    SupplierSupplyType supplyType,
    SupplierPaymentTerms paymentTerms,
    UUID categoryId,
    Integer creditDays,
    String observations) {
  public static final SupplierProfile EMPTY =
      new SupplierProfile(null, null, null, null, null, null);

  public SupplierProfile {
    address = optionalText(address, 500, "address");
    observations = optionalText(observations, 1000, "observations");
    if (creditDays != null
        && (creditDays < 0 || paymentTerms == null || paymentTerms == SupplierPaymentTerms.CASH)) {
      throw new InvalidSupplierDetails("creditDays");
    }
  }

  public boolean supplies(CatalogItemType type) {
    return supplyType == SupplierSupplyType.REGULAR
        || supplyType == SupplierSupplyType.BOTH
        || (supplyType != null && supplyType.name().equals(type.name()));
  }

  static String optionalText(String text, int limit, String field) {
    if (text == null) {
      return null;
    }
    if (text.length() > limit || text.indexOf(0) >= 0) {
      throw new InvalidSupplierDetails(field);
    }
    return text.isBlank() ? null : text.strip();
  }

  static String normalizeTaxId(String value, boolean commercial) {
    String text = optionalText(value, SupplierDetails.TAX_ID_LIMIT, "taxId");
    if (text == null) {
      return null;
    }
    if (text.matches("[0-9][0-9 -]*[0-9]")) {
      String digits = text.replace(" ", "").replace("-", "");
      if (digits.length() == 9 || digits.length() == 11) {
        return digits;
      }
    }
    if (commercial) {
      throw new InvalidSupplierDetails("taxId");
    }
    return text;
  }
}
