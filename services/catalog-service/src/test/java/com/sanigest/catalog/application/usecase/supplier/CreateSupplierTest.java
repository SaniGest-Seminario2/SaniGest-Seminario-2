package com.sanigest.catalog.application.usecase.supplier;

import static com.sanigest.catalog.application.port.out.integration.company.CatalogAuthorization.Permission.CATALOG_WRITE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sanigest.catalog.application.exception.security.CatalogAuthorizationUnavailable;
import com.sanigest.catalog.application.exception.supplier.SupplierCreationException;
import com.sanigest.catalog.application.port.out.integration.company.CatalogAuthorization;
import com.sanigest.catalog.application.port.out.persistence.supplier.SupplierItemRepository;
import com.sanigest.catalog.application.port.out.persistence.supplier.SupplierRepository;
import com.sanigest.catalog.domain.enums.catalogitem.CatalogItemType;
import com.sanigest.catalog.domain.enums.supplier.SupplierPaymentTerms;
import com.sanigest.catalog.domain.enums.supplier.SupplierStatus;
import com.sanigest.catalog.domain.enums.supplier.SupplierSupplyType;
import com.sanigest.catalog.domain.model.supplier.SupplierDetails;
import com.sanigest.catalog.domain.model.supplier.SupplierItemOffer;
import com.sanigest.catalog.domain.model.supplier.SupplierProfile;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreateSupplierTest {
  private final CatalogAuthorization authorization = mock(CatalogAuthorization.class);
  private final SupplierRepository suppliers = mock(SupplierRepository.class);
  private final SupplierItemRepository items = mock();
  private final SupplierItemOffer offer =
      new SupplierItemOffer(UUID.randomUUID(), BigDecimal.ONE, "SUP-1", false);
  private final CreateSupplier create =
      new CreateSupplier(Optional.of(authorization), suppliers, items);
  private final SupplierDetails details =
      new SupplierDetails(
          "Supplier",
          null,
          "Contact",
          null,
          null,
          new SupplierProfile(
              null, SupplierSupplyType.BOTH, SupplierPaymentTerms.CASH, null, null, null));

  @Test
  void registersAnActiveSupplierInTheAuthorizedCompanyWithAGeneratedId() {
    var company = UUID.randomUUID();
    when(authorization.membership(CATALOG_WRITE)).thenReturn(Optional.of(company));

    when(items.lockItems(eq(company), any(), eq(List.of(offer.catalogItemId()))))
        .thenReturn(
            Map.of(
                offer.catalogItemId(),
                new SupplierItemRepository.LockedItem(CatalogItemType.PRODUCT, true)));
    var supplier = create.execute(details, SupplierStatus.ACTIVE, List.of(offer));

    assertThat(supplier.id()).isNotNull();
    assertThat(supplier.companyId()).isEqualTo(company);
    assertThat(supplier.status()).isEqualTo(SupplierStatus.ACTIVE);
    assertThat(supplier.details()).isEqualTo(details);
    verify(suppliers).insert(supplier);
    verify(items).apply(company, supplier.id(), offer);
  }

  @Test
  void deniesCreationWhenMembershipDoesNotAuthorizeWrite() {
    when(authorization.membership(CATALOG_WRITE)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> create.execute(details, SupplierStatus.ACTIVE, List.of(offer)))
        .isInstanceOfSatisfying(
            SupplierCreationException.class,
            failure ->
                assertThat(failure.reason()).isEqualTo(SupplierCreationException.Reason.FORBIDDEN));
    verifyNoInteractions(suppliers);
  }

  @Test
  void failsClosedWhenCompanyIsUnavailable() {
    when(authorization.membership(CATALOG_WRITE)).thenThrow(new CatalogAuthorizationUnavailable());

    assertThatThrownBy(() -> create.execute(details, SupplierStatus.ACTIVE, List.of(offer)))
        .isInstanceOf(CatalogAuthorizationUnavailable.class);
    verifyNoInteractions(suppliers);
  }

  @Test
  void failsClosedWhenNoAuthorizationAdapterIsConfigured() {
    var withoutCompany = new CreateSupplier(Optional.empty(), suppliers, items);

    assertThatThrownBy(() -> withoutCompany.execute(details, SupplierStatus.ACTIVE, List.of(offer)))
        .isInstanceOf(CatalogAuthorizationUnavailable.class);
    verifyNoInteractions(suppliers);
  }
}
