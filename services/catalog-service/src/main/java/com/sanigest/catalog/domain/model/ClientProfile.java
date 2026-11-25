package com.sanigest.catalog.domain.model;

import com.sanigest.catalog.domain.enums.ClientPaymentTerms;
import com.sanigest.catalog.domain.enums.ClientType;
import com.sanigest.catalog.domain.exception.InvalidClientPatch;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record ClientProfile(
    ClientType clientType,
    @Pattern(regexp = "[0-9+() .-]{7,32}") String alternatePhone,
    @Pattern(regexp = "[A-Z]{2}") String countryCode,
    @Size(max = 8) String provinceCode,
    @Size(max = 8) String municipalityCode,
    @Size(max = 32) String preferredReceiptType,
    ClientPaymentTerms paymentTerms,
    UUID categoryId,
    BigDecimal creditLimit,
    @Size(max = 1000) String observations) {
  private static final BigDecimal LIMIT = BigDecimal.TEN.pow(17);

  public ClientProfile {
    if (creditLimit != null) {
      if (creditLimit.signum() < 0
          || creditLimit.scale() > 2
          || creditLimit.compareTo(LIMIT) >= 0) {
        throw new InvalidClientPatch();
      }
      creditLimit = creditLimit.setScale(2);
    }
    if ((provinceCode != null && countryCode == null)
        || (municipalityCode != null && provinceCode == null)) {
      throw new InvalidClientPatch();
    }
  }

  public ClientProfile defaults(UUID defaultCategoryId) {
    return new ClientProfile(
        clientType,
        alternatePhone,
        countryCode == null ? "DO" : countryCode,
        provinceCode,
        municipalityCode,
        preferredReceiptType,
        paymentTerms == null ? ClientPaymentTerms.CASH : paymentTerms,
        categoryId == null ? defaultCategoryId : categoryId,
        creditLimit,
        observations);
  }

  public void requireComplete() {
    if (clientType == null || countryCode == null || paymentTerms == null || categoryId == null) {
      throw new InvalidClientPatch();
    }
  }

  String normalizeTaxId(String taxId) {
    if (taxId == null || !"DO".equals(countryCode)) {
      return taxId;
    }
    if (!taxId.matches("[0-9][0-9 -]*[0-9]")) {
      throw new InvalidClientPatch();
    }
    String digits = taxId.replaceAll("[ -]", "");
    if (digits.length() != (clientType == ClientType.NATURAL ? 11 : 9)) {
      throw new InvalidClientPatch();
    }
    return digits;
  }
}
