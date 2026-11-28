package com.sanigest.catalog.application.usecase.supplier;

import static com.sanigest.catalog.application.exception.supplier.SupplierAccessException.Reason.SUPPLIER_NOT_FOUND;
import static com.sanigest.catalog.application.port.out.integration.company.CatalogAuthorization.Permission.CATALOG_READ;

import com.sanigest.catalog.application.exception.supplier.SupplierAccessException;
import com.sanigest.catalog.application.port.out.persistence.supplier.SupplierRepository;
import com.sanigest.catalog.domain.model.supplier.Supplier;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class GetSupplier {
  private final SupplierCompany company;
  private final SupplierRepository suppliers;

  GetSupplier(SupplierCompany company, SupplierRepository suppliers) {
    this.company = company;
    this.suppliers = suppliers;
  }

  @PreAuthorize(
      "T(com.sanigest.companyaccess.security.authentication.AuthenticatedActor).userId().isPresent()")
  public Supplier execute(UUID id) {
    return suppliers
        .find(company.require(CATALOG_READ), id)
        .orElseThrow(() -> new SupplierAccessException(SUPPLIER_NOT_FOUND));
  }
}
