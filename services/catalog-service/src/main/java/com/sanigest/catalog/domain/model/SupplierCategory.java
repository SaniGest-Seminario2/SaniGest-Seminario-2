package com.sanigest.catalog.domain.model;

import com.sanigest.catalog.domain.enums.SupplierStatus;
import com.sanigest.catalog.domain.exception.InvalidSupplierPatch;
import java.util.UUID;

public record SupplierCategory(
    UUID id, UUID companyId, String code, String name, SupplierStatus status) {
  public SupplierCategory {
    if (code == null
        || !code.matches("[A-Z][A-Z0-9_]{0,31}")
        || name == null
        || name.isBlank()
        || name.length() > 100
        || name.indexOf(0) >= 0
        || status == null) {
      throw new InvalidSupplierPatch();
    }
    name = name.strip();
  }
}
