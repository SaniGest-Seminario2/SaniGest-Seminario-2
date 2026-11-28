package com.sanigest.catalog.application.usecase.supplier;

import static com.sanigest.catalog.application.exception.catalogitem.CatalogItemAccessException.Reason.CATALOG_ITEM_NOT_FOUND;
import static com.sanigest.catalog.application.exception.supplier.SupplierCommercialException.Reason.INCOMPATIBLE_SUPPLIER_ITEM;
import static com.sanigest.catalog.application.exception.supplier.SupplierCommercialException.Reason.PRIMARY_SUPPLIER_CONFLICT;
import static com.sanigest.catalog.application.port.out.integration.company.CatalogAuthorization.Permission.CATALOG_WRITE;

import com.sanigest.catalog.application.exception.catalogitem.CatalogItemAccessException;
import com.sanigest.catalog.application.exception.supplier.SupplierCommercialException;
import com.sanigest.catalog.application.port.out.persistence.supplier.SupplierItemRepository;
import com.sanigest.catalog.domain.enums.supplier.SupplierStatus;
import com.sanigest.catalog.domain.exception.supplier.InvalidSupplierPatch;
import com.sanigest.catalog.domain.model.supplier.SupplierItemOffer;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReplaceSupplierItems {
  public static final int MAX_ITEMS = 100;
  private final SupplierCompany company;
  private final SupplierItemRepository items;

  ReplaceSupplierItems(SupplierCompany company, SupplierItemRepository items) {
    this.company = company;
    this.items = items;
  }

  @Transactional
  @PreAuthorize(
      "T(com.sanigest.companyaccess.security.authentication.AuthenticatedActor).userId().isPresent()")
  public void execute(UUID supplierId, List<SupplierItemOffer> requested, boolean replacePrimary) {
    var companyId = company.require(CATALOG_WRITE);
    var ids = requested.stream().map(SupplierItemOffer::catalogItemId).distinct().toList();
    if (requested.size() > MAX_ITEMS || ids.size() != requested.size()) {
      throw new InvalidSupplierPatch();
    }

    var supplier = items.lockSupplier(companyId, supplierId);
    var locked = items.lockItems(companyId, supplierId, ids);
    for (var offer : requested) {
      var item = locked.get(offer.catalogItemId());
      validateOffer(supplier, item);
      if (offer.primary()
          && !replacePrimary
          && items.hasOtherPrimary(companyId, supplierId, offer.catalogItemId())) {
        throw new SupplierCommercialException(PRIMARY_SUPPLIER_CONFLICT);
      }
    }

    items.retireOmitted(companyId, supplierId, ids);
    for (var offer : requested) {
      if (offer.primary() && replacePrimary) {
        items.clearOtherPrimary(companyId, supplierId, offer.catalogItemId());
      }
      items.apply(companyId, supplierId, offer);
    }
  }

  private static void validateOffer(
      SupplierItemRepository.LockedSupplier supplier, SupplierItemRepository.LockedItem item) {
    if (item == null) {
      throw new CatalogItemAccessException(CATALOG_ITEM_NOT_FOUND);
    }
    if (supplier.status() != SupplierStatus.ACTIVE
        || !item.active()
        || Boolean.FALSE.equals(item.availableForPurchase())
        || !supplier.profile().supplies(item.type())) {
      throw new SupplierCommercialException(INCOMPATIBLE_SUPPLIER_ITEM);
    }
  }
}
