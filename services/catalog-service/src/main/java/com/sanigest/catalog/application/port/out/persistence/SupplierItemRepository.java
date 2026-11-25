package com.sanigest.catalog.application.port.out.persistence;

import com.sanigest.catalog.application.dto.SupplierItemPage;
import com.sanigest.catalog.application.dto.SupplierItemQuery;
import com.sanigest.catalog.domain.enums.CatalogItemType;
import com.sanigest.catalog.domain.enums.SupplierStatus;
import com.sanigest.catalog.domain.model.SupplierItemOffer;
import com.sanigest.catalog.domain.model.SupplierProfile;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface SupplierItemRepository {
  LockedSupplier lockSupplier(UUID companyId, UUID supplierId);

  Map<UUID, LockedItem> lockItems(UUID companyId, UUID supplierId, List<UUID> requestedIds);

  void retireOmitted(UUID companyId, UUID supplierId, List<UUID> requestedIds);

  boolean hasOtherPrimary(UUID companyId, UUID supplierId, UUID itemId);

  void clearOtherPrimary(UUID companyId, UUID supplierId, UUID itemId);

  void apply(UUID companyId, UUID supplierId, SupplierItemOffer offer);

  SupplierItemPage search(SupplierItemQuery query);

  record LockedSupplier(SupplierStatus status, SupplierProfile profile) {}

  record LockedItem(CatalogItemType type, boolean active, Boolean availableForPurchase) {
    public LockedItem(CatalogItemType type, boolean active) {
      this(type, active, null);
    }
  }
}
