package com.sanigest.catalog.application.usecase.catalogitem;

import static com.sanigest.catalog.application.port.out.integration.CatalogAuthorization.Permission.CATALOG_WRITE;
import static com.sanigest.catalog.domain.enums.CatalogItemType.SERVICE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sanigest.catalog.application.exception.CatalogAuthorizationUnavailable;
import com.sanigest.catalog.application.exception.CatalogItemCreationForbidden;
import com.sanigest.catalog.application.port.out.integration.CatalogAuthorization;
import com.sanigest.catalog.application.port.out.persistence.CatalogItemCommercialRepository;
import com.sanigest.catalog.application.port.out.persistence.CatalogItemRepository;
import com.sanigest.catalog.domain.exception.InvalidCatalogItem;
import com.sanigest.catalog.domain.model.CatalogItemProfile;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreateCatalogItemTest {
  private final CatalogItemCommercialRepository references =
      mock(CatalogItemCommercialRepository.class);
  private static final CatalogItemProfile PROFILE =
      new CatalogItemProfile(null, null, null, "UNIT", null, "EXEMPT", true, false);
  private final CatalogItemWritePolicy policy =
      new CatalogItemWritePolicy(
          references, new CatalogBusinessDate(java.time.Clock.systemUTC()), false);
  private final CatalogAuthorization authorization = mock(CatalogAuthorization.class);
  private final CatalogItemRepository items = mock(CatalogItemRepository.class);
  private final CreateCatalogItem create =
      new CreateCatalogItem(Optional.of(authorization), items, policy);

  @Test
  void persistsTheItemForTheCompanyResolvedWithWritePermission() {
    var company = UUID.randomUUID();
    when(authorization.membership(CATALOG_WRITE)).thenReturn(Optional.of(company));

    when(references.activeUnit("UNIT")).thenReturn(true);
    when(references.currentTax(
            org.mockito.ArgumentMatchers.eq("EXEMPT"), org.mockito.ArgumentMatchers.any()))
        .thenReturn(true);
    var item = create.execute("Consulting", SERVICE, new BigDecimal("15000"), null, PROFILE);

    assertThat(item.companyId()).isEqualTo(company);
    assertThat(item.price()).isEqualTo(new BigDecimal("15000.00"));
    verify(items).insert(item);
  }

  @Test
  void deniesAnActorWithoutWriteMembership() {
    when(authorization.membership(CATALOG_WRITE)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> create.execute("Consulting", SERVICE, BigDecimal.ONE, null, PROFILE))
        .isInstanceOf(CatalogItemCreationForbidden.class);
    verifyNoInteractions(items);
  }

  @Test
  void failsClosedWhenAuthorizationIsDisabledOrUnavailable() {
    var disabled = new CreateCatalogItem(Optional.empty(), items, policy);
    assertThatThrownBy(() -> disabled.execute("Consulting", SERVICE, BigDecimal.ONE, null, PROFILE))
        .isInstanceOf(CatalogAuthorizationUnavailable.class);

    when(authorization.membership(CATALOG_WRITE)).thenThrow(new CatalogAuthorizationUnavailable());
    assertThatThrownBy(() -> create.execute("Consulting", SERVICE, BigDecimal.ONE, null, PROFILE))
        .isInstanceOf(CatalogAuthorizationUnavailable.class);
    verifyNoInteractions(items);
  }

  @Test
  void doesNotInsertAnInvalidItemEvenForAnAuthorizedMember() {
    when(authorization.membership(CATALOG_WRITE)).thenReturn(Optional.of(UUID.randomUUID()));

    assertThatThrownBy(
            () -> create.execute("Consulting", SERVICE, new BigDecimal("-0.01"), null, PROFILE))
        .isInstanceOf(InvalidCatalogItem.class);
    verifyNoInteractions(items);
  }
}
