package com.sanigest.catalog.api.dto;

import com.sanigest.catalog.domain.enums.SupplierSupplyType;

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
