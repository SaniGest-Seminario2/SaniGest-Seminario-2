package com.sanigest.catalog.application.usecase.supplier;

import static com.sanigest.catalog.application.exception.SupplierCreationException.Reason.FORBIDDEN;
import static com.sanigest.catalog.application.port.out.integration.CatalogAuthorization.Permission.CATALOG_WRITE;

import com.sanigest.catalog.application.exception.CatalogAuthorizationUnavailable;
import com.sanigest.catalog.application.exception.SupplierCreationException;
import com.sanigest.catalog.application.port.out.integration.CatalogAuthorization;
import com.sanigest.catalog.application.port.out.persistence.SupplierRepository;
import com.sanigest.catalog.domain.exception.InvalidSupplierPatch;
import com.sanigest.catalog.domain.model.Supplier;
import com.sanigest.catalog.domain.model.SupplierDetails;
import java.util.Optional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class CreateSupplier {
  private final Optional<CatalogAuthorization> authorization;
  private final SupplierRepository suppliers;

  public CreateSupplier(
      Optional<CatalogAuthorization> authorization, SupplierRepository suppliers) {
    this.authorization = authorization;
    this.suppliers = suppliers;
  }

  @PreAuthorize("T(com.sanigest.companyaccess.AuthenticatedActor).userId().isPresent()")
  public Supplier execute(SupplierDetails details) {
    var companyId =
        authorization
            .orElseThrow(CatalogAuthorizationUnavailable::new)
            .membership(CATALOG_WRITE)
            .orElseThrow(() -> new SupplierCreationException(FORBIDDEN));
    if (details.profile().paymentTerms() == null) {
      throw new InvalidSupplierPatch();
    }
    var supplier = Supplier.register(companyId, details);
    suppliers.insert(supplier);
    return supplier;
  }
}
