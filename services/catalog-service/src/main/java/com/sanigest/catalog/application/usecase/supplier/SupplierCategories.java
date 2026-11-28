package com.sanigest.catalog.application.usecase.supplier;

import static com.sanigest.catalog.application.port.out.integration.company.CatalogAuthorization.Permission.CATALOG_READ;

import com.sanigest.catalog.application.port.out.persistence.supplier.SupplierCategoryRepository;
import com.sanigest.catalog.domain.model.supplier.SupplierCategory;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize(
    "T(com.sanigest.companyaccess.security.authentication.AuthenticatedActor).userId().isPresent()")
public class SupplierCategories {
  private final SupplierCompany company;
  private final SupplierCategoryRepository categories;

  SupplierCategories(SupplierCompany company, SupplierCategoryRepository categories) {
    this.company = company;
    this.categories = categories;
  }

  @Transactional
  public List<SupplierCategory> list() {
    var companyId = company.require(CATALOG_READ);
    categories.initialize(companyId);
    return categories.list(companyId);
  }
}
