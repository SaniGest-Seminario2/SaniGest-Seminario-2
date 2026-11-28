package com.sanigest.catalog.api.dto.supplier;

import com.sanigest.catalog.domain.enums.supplier.SupplierSupplyType;

public enum SupplierSupplyCoverage {
  PRODUCT,
  SERVICE,
  BOTH;

  static SupplierSupplyCoverage from(SupplierSupplyType type) {
    if (type == null) {
      return null;
    }
    return switch (type) {
      case PRODUCT -> PRODUCT;
      case SERVICE -> SERVICE;
      case BOTH, REGULAR -> BOTH;
    };
  }
}
