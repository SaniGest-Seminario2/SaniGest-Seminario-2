package com.sanigest.catalog.application.usecase.supplier;

import static com.sanigest.catalog.application.port.out.integration.company.CatalogAuthorization.Permission.CATALOG_WRITE;

import com.sanigest.catalog.application.port.out.persistence.supplier.SupplierRepository;
import com.sanigest.catalog.domain.model.supplier.Supplier;
import com.sanigest.catalog.domain.model.supplier.SupplierPatch;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class UpdateSupplier {
  private final SupplierCompany company;
  private final SupplierRepository suppliers;

  UpdateSupplier(SupplierCompany company, SupplierRepository suppliers) {
    this.company = company;
    this.suppliers = suppliers;
  }

  @PreAuthorize(
      "T(com.sanigest.companyaccess.security.authentication.AuthenticatedActor).userId().isPresent()")
  public Supplier execute(UUID id, SupplierPatch patch) {
    var companyId = company.require(CATALOG_WRITE);
    return suppliers.update(companyId, id, supplier -> supplier.updateDetails(patch));
  }
}
