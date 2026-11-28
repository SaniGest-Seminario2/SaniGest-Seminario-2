package com.sanigest.catalog.application.usecase.supplier;

import static com.sanigest.catalog.application.port.out.integration.company.CatalogAuthorization.Permission.CATALOG_WRITE;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sanigest.catalog.application.exception.catalogitem.CatalogItemAccessException;
import com.sanigest.catalog.application.exception.supplier.SupplierCommercialException;
import com.sanigest.catalog.application.port.out.integration.company.CatalogAuthorization;
import com.sanigest.catalog.application.port.out.persistence.supplier.SupplierItemRepository;
import com.sanigest.catalog.application.port.out.persistence.supplier.SupplierItemRepository.LockedItem;
import com.sanigest.catalog.application.port.out.persistence.supplier.SupplierItemRepository.LockedSupplier;
import com.sanigest.catalog.domain.enums.catalogitem.CatalogItemType;
import com.sanigest.catalog.domain.enums.supplier.SupplierStatus;
import com.sanigest.catalog.domain.enums.supplier.SupplierSupplyType;
import com.sanigest.catalog.domain.exception.supplier.InvalidSupplierPatch;
import com.sanigest.catalog.domain.model.supplier.SupplierItemOffer;
import com.sanigest.catalog.domain.model.supplier.SupplierProfile;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReplaceSupplierItemsTest {
  private final CatalogAuthorization authorization = mock(CatalogAuthorization.class);
  private final SupplierItemRepository items = mock(SupplierItemRepository.class);
  private final ReplaceSupplierItems replace =
      new ReplaceSupplierItems(new SupplierCompany(Optional.of(authorization)), items);
  private final UUID tenant = UUID.randomUUID();
  private final UUID supplier = UUID.randomUUID();
  private final UUID item = UUID.randomUUID();
  private final SupplierItemOffer primary = new SupplierItemOffer(item, null, null, true);

  ReplaceSupplierItemsTest() {
    when(authorization.membership(CATALOG_WRITE)).thenReturn(Optional.of(tenant));
  }

  @Test
  void rejectsDuplicateIdsBeforePersistence() {
    assertThatThrownBy(() -> replace.execute(supplier, List.of(primary, primary), false))
        .isInstanceOf(InvalidSupplierPatch.class);
    verifyNoInteractions(items);
  }

  @Test
  void validatesWholeSetBeforeClearingExistingPrimary() {
    var missing = UUID.randomUUID();
    when(items.lockSupplier(tenant, supplier)).thenReturn(active(SupplierSupplyType.BOTH));
    when(items.lockItems(tenant, supplier, List.of(item, missing)))
        .thenReturn(Map.of(item, new LockedItem(CatalogItemType.PRODUCT, true)));
    var requested = List.of(primary, new SupplierItemOffer(missing, null, null, false));

    assertThatThrownBy(() -> replace.execute(supplier, requested, true))
        .isInstanceOf(CatalogItemAccessException.class);
    var order = inOrder(items);
    order.verify(items).lockSupplier(tenant, supplier);
    order.verify(items).lockItems(tenant, supplier, List.of(item, missing));
    order.verifyNoMoreInteractions();
  }

  @Test
  void requiresExplicitPrimaryReplacementAndClearsBeforeApplying() {
    var ids = List.of(item);
    when(items.lockSupplier(tenant, supplier)).thenReturn(active(SupplierSupplyType.BOTH));
    when(items.lockItems(tenant, supplier, ids))
        .thenReturn(Map.of(item, new LockedItem(CatalogItemType.PRODUCT, true)));
    when(items.hasOtherPrimary(tenant, supplier, item)).thenReturn(true);

    assertThatThrownBy(() -> replace.execute(supplier, List.of(primary), false))
        .isInstanceOf(SupplierCommercialException.class)
        .hasMessage("PRIMARY_SUPPLIER_CONFLICT");

    replace.execute(supplier, List.of(primary), true);
    var order = inOrder(items);
    order.verify(items).retireOmitted(tenant, supplier, ids);
    order.verify(items).clearOtherPrimary(tenant, supplier, item);
    order.verify(items).apply(tenant, supplier, primary);
  }

  @Test
  void rejectsInactiveOrIncompatibleParties() {
    when(items.lockSupplier(tenant, supplier))
        .thenReturn(
            new LockedSupplier(SupplierStatus.INACTIVE, SupplierProfile.EMPTY),
            active(SupplierSupplyType.BOTH),
            active(SupplierSupplyType.SERVICE),
            active(null));
    when(items.lockItems(tenant, supplier, List.of(item)))
        .thenReturn(
            Map.of(item, new LockedItem(CatalogItemType.PRODUCT, true)),
            Map.of(item, new LockedItem(CatalogItemType.PRODUCT, false)),
            Map.of(item, new LockedItem(CatalogItemType.PRODUCT, true)));
    for (int scenario = 0; scenario < 4; scenario++) {
      assertThatThrownBy(() -> replace.execute(supplier, List.of(primary), true))
          .isInstanceOf(SupplierCommercialException.class)
          .hasMessage("INCOMPATIBLE_SUPPLIER_ITEM");
    }
  }

  private static LockedSupplier active(SupplierSupplyType supplyType) {
    return new LockedSupplier(
        SupplierStatus.ACTIVE, new SupplierProfile(null, supplyType, null, null, null, null));
  }
}
