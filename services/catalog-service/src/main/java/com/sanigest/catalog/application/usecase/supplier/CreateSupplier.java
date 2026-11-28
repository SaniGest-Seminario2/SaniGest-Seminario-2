package com.sanigest.catalog.application.usecase.supplier;

import static com.sanigest.catalog.application.exception.supplier.SupplierCreationException.Reason.FORBIDDEN;
import static com.sanigest.catalog.application.port.out.integration.company.CatalogAuthorization.Permission.CATALOG_WRITE;

import com.sanigest.catalog.application.exception.security.CatalogAuthorizationUnavailable;
import com.sanigest.catalog.application.exception.supplier.SupplierCreationException;
import com.sanigest.catalog.application.port.out.integration.company.CatalogAuthorization;
import com.sanigest.catalog.application.port.out.persistence.supplier.SupplierItemRepository;
import com.sanigest.catalog.application.port.out.persistence.supplier.SupplierRepository;
import com.sanigest.catalog.domain.enums.supplier.SupplierStatus;
import com.sanigest.catalog.domain.exception.supplier.InvalidSupplierPatch;
import com.sanigest.catalog.domain.model.supplier.Supplier;
import com.sanigest.catalog.domain.model.supplier.SupplierDetails;
import com.sanigest.catalog.domain.model.supplier.SupplierItemOffer;
import java.util.List;
import java.util.Optional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateSupplier {
  private final Optional<CatalogAuthorization> authorization;
  private final SupplierRepository suppliers;
  private final SupplierItemRepository items;

  public CreateSupplier(
      Optional<CatalogAuthorization> authorization,
      SupplierRepository suppliers,
      SupplierItemRepository items) {
    this.authorization = authorization;
    this.suppliers = suppliers;
    this.items = items;
  }

  @Transactional
  @PreAuthorize(
      "T(com.sanigest.companyaccess.security.authentication.AuthenticatedActor).userId().isPresent()")
  public Supplier execute(
      SupplierDetails details, SupplierStatus status, List<SupplierItemOffer> offers) {
    var companyId =
        authorization
            .orElseThrow(CatalogAuthorizationUnavailable::new)
            .membership(CATALOG_WRITE)
            .orElseThrow(() -> new SupplierCreationException(FORBIDDEN));
    if (details.profile().paymentTerms() == null) {
      throw new InvalidSupplierPatch();
    }
    var ids = offers.stream().map(SupplierItemOffer::catalogItemId).distinct().toList();
    if (offers.isEmpty()
        || offers.size() > ReplaceSupplierItems.MAX_ITEMS
        || ids.size() != offers.size()) {
      throw new InvalidSupplierPatch();
    }
    var supplier = Supplier.register(companyId, details).changeStatus(status);
    var locked = items.lockItems(companyId, supplier.id(), ids);
    for (var offer : offers) {
      if (offer.offeredCost() == null || offer.supplierReference() == null || offer.primary()) {
        throw new InvalidSupplierPatch();
      }
      ReplaceSupplierItems.validateItem(
          supplier.details().profile(), locked.get(offer.catalogItemId()));
    }
    suppliers.insert(supplier);
    for (var offer : offers) {
      items.apply(companyId, supplier.id(), offer);
    }
    if (status == SupplierStatus.INACTIVE) {
      items.retireOmitted(companyId, supplier.id(), List.of());
    }
    return supplier;
  }
}
