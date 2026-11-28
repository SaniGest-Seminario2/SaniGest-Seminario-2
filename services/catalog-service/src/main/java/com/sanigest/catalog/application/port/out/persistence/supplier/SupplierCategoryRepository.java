package com.sanigest.catalog.application.port.out.persistence.supplier;

import com.sanigest.catalog.domain.model.supplier.SupplierCategory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupplierCategoryRepository {
  void initialize(UUID companyId);

  List<SupplierCategory> list(UUID companyId);

  Optional<SupplierCategory> lockForAssignment(UUID companyId, UUID id);
}
