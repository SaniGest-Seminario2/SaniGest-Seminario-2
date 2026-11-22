package com.sanigest.catalog.application.port.out.persistence;

import com.sanigest.catalog.domain.model.Supplier;

public interface SupplierRepository {
  void insert(Supplier supplier);
}
