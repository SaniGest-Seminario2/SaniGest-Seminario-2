package com.sanigest.catalog.application.port.out.persistence;

import com.sanigest.catalog.application.dto.SupplierPage;
import com.sanigest.catalog.application.dto.SupplierSearchCriteria;
import com.sanigest.catalog.domain.enums.SupplierStatus;
import com.sanigest.catalog.domain.model.Supplier;
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
