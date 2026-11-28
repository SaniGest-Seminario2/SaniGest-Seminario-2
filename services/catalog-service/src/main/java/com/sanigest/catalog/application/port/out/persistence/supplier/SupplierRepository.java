package com.sanigest.catalog.application.port.out.persistence.supplier;

import com.sanigest.catalog.application.dto.supplier.SupplierPage;
import com.sanigest.catalog.application.dto.supplier.SupplierSearchCriteria;
import com.sanigest.catalog.domain.enums.supplier.SupplierStatus;
import com.sanigest.catalog.domain.model.supplier.Supplier;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

public interface SupplierRepository {
  void insert(Supplier supplier);

  Optional<Supplier> find(UUID companyId, UUID id);

  Supplier update(UUID companyId, UUID id, UnaryOperator<Supplier> change);

  SupplierPage search(UUID companyId, SupplierSearchCriteria filter, int page, int size);

  SupplierPage search(UUID companyId, String search, SupplierStatus status, int page, int size);
}
