package com.sanigest.catalog.application.usecase.supplier;

import static com.sanigest.catalog.application.exception.SupplierCommercialException.Reason.DUPLICATE_SUPPLIER_CATEGORY;
import static com.sanigest.catalog.application.exception.SupplierCommercialException.Reason.SUPPLIER_CATEGORY_NOT_FOUND;
import static com.sanigest.catalog.application.port.out.integration.CatalogAuthorization.Permission.CATALOG_READ;
import static com.sanigest.catalog.application.port.out.integration.CatalogAuthorization.Permission.CATALOG_WRITE;

import com.sanigest.catalog.application.exception.SupplierCommercialException;
import com.sanigest.catalog.application.port.out.persistence.SupplierCategoryRepository;
import com.sanigest.catalog.domain.enums.SupplierStatus;
import com.sanigest.catalog.domain.model.SupplierCategory;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("T(com.sanigest.companyaccess.AuthenticatedActor).userId().isPresent()")
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

  @Transactional
  public SupplierCategory create(String code, String name) {
    var companyId = company.require(CATALOG_WRITE);
    var category =
        new SupplierCategory(UUID.randomUUID(), companyId, code, name, SupplierStatus.ACTIVE);
    categories.initialize(companyId);
    if (!categories.insert(category)) {
      throw new SupplierCommercialException(DUPLICATE_SUPPLIER_CATEGORY);
    }
    return category;
  }

  @Transactional
  public SupplierCategory update(UUID id, String name, SupplierStatus status) {
    var companyId = company.require(CATALOG_WRITE);
    var category =
        categories
            .lockForEdit(companyId, id)
            .orElseThrow(() -> new SupplierCommercialException(SUPPLIER_CATEGORY_NOT_FOUND));
    var updated = category.revise(name, status);
    categories.update(updated);
    return updated;
  }
}
