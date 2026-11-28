package com.sanigest.catalog.application.usecase.supplier;

import static com.sanigest.catalog.application.port.out.integration.company.CatalogAuthorization.Permission.CATALOG_WRITE;

import com.sanigest.catalog.application.port.out.persistence.supplier.SupplierRepository;
import com.sanigest.catalog.domain.enums.supplier.SupplierStatus;
import com.sanigest.catalog.domain.model.supplier.Supplier;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class ChangeSupplierStatus {
  private final SupplierCompany company;
  private final SupplierRepository suppliers;

  ChangeSupplierStatus(SupplierCompany company, SupplierRepository suppliers) {
    this.company = company;
    this.suppliers = suppliers;
  }

  @PreAuthorize(
      "T(com.sanigest.companyaccess.security.authentication.AuthenticatedActor).userId().isPresent()")
  public Supplier execute(UUID id, SupplierStatus status) {
    return suppliers.update(
        company.require(CATALOG_WRITE), id, supplier -> supplier.changeStatus(status));
  }
}
