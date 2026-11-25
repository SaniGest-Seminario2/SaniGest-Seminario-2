package com.sanigest.catalog.application.usecase.catalogitem;

import static com.sanigest.catalog.application.exception.CatalogItemAccessException.Reason.CATALOG_ITEM_NOT_FOUND;
import static com.sanigest.catalog.application.exception.CatalogItemAccessException.Reason.CATALOG_ITEM_UPDATE_CONFLICT;
import static com.sanigest.catalog.application.port.out.integration.CatalogAuthorization.Permission.CATALOG_READ;
import static com.sanigest.catalog.application.port.out.integration.CatalogAuthorization.Permission.CATALOG_WRITE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sanigest.catalog.application.dto.CatalogItemPage;
import com.sanigest.catalog.application.exception.CatalogAuthorizationUnavailable;
import com.sanigest.catalog.application.exception.CatalogItemAccessException;
import com.sanigest.catalog.application.exception.InvalidCatalogItemQuery;
import com.sanigest.catalog.application.port.out.integration.CatalogAuthorization;
import com.sanigest.catalog.application.port.out.persistence.CatalogItemRepository;
import com.sanigest.catalog.domain.enums.CatalogItemType;
import com.sanigest.catalog.domain.model.CatalogItem;
import com.sanigest.catalog.domain.model.CatalogItemPatch;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ManageCatalogItemsTest {
  private final CatalogItemWritePolicy policy =
      new CatalogItemWritePolicy(
          mock(
              com.sanigest.catalog.application.port.out.persistence.CatalogItemCommercialRepository
                  .class),
          new CatalogBusinessDate(java.time.Clock.systemUTC()),
          false);
  private final CatalogAuthorization authorization = mock(CatalogAuthorization.class);
  private final CatalogItemRepository repository = mock(CatalogItemRepository.class);
  private final CatalogItemCompany company = new CatalogItemCompany(Optional.of(authorization));
  private final UUID tenant = UUID.randomUUID();
  private final CatalogItem item =
      CatalogItem.register(tenant, "Silla", CatalogItemType.PRODUCT, new BigDecimal("3200"));

  @ParameterizedTest
  @CsvSource({
    ",,,",
    "PRODUCT,,,",
    "SERVICE,,,",
    ", ' sil ',,sil",
    ",,true,",
    ",,false,",
    "PRODUCT, ' sil ',false,sil"
  })
  void scopesAndNormalizesOptionalFilters(String type, String search, String active, String term) {
    var expected = new CatalogItemPage(List.of(), 2, 20, 0);
    when(authorization.membership(CATALOG_READ)).thenReturn(Optional.of(tenant));
    when(repository.search(
            tenant,
            type == null ? null : CatalogItemType.valueOf(type),
            term,
            active == null ? null : Boolean.valueOf(active),
            2,
            20))
        .thenReturn(expected);
    assertThat(new ListCatalogItems(company, repository).execute(type, search, active, 2, 20))
        .isSameAs(expected);
  }

  @ParameterizedTest
  @CsvSource({",, -1,20", ",,0,0", ",,0,101", "BAD,,0,20", ",TRUE,0,20", ",1,0,20"})
  void rejectsInvalidQueries(String type, String active, int page, int size) {
    when(authorization.membership(CATALOG_READ)).thenReturn(Optional.of(tenant));
    assertThatThrownBy(
            () -> new ListCatalogItems(company, repository).execute(type, null, active, page, size))
        .isInstanceOf(InvalidCatalogItemQuery.class);
    verifyNoInteractions(repository);
  }

  @Test
  void returnsOnlyTenantScopedDetails() {
    when(authorization.membership(CATALOG_READ)).thenReturn(Optional.of(tenant));
    when(repository.find(tenant, item.id())).thenReturn(Optional.of(item));
    var get = new GetCatalogItem(company, repository);
    assertThat(get.execute(item.id())).isEqualTo(item);
    assertThatThrownBy(() -> get.execute(UUID.randomUUID()))
        .isInstanceOf(CatalogItemAccessException.class)
        .hasMessage(CATALOG_ITEM_NOT_FOUND.name());
  }

  @Test
  void appliesAnEditAndBothStateTransitionsThroughTheTenantScopedPort() {
    when(authorization.membership(CATALOG_WRITE)).thenReturn(Optional.of(tenant));
    when(repository.update(eq(tenant), eq(item.id()), any()))
        .thenAnswer(invocation -> {
          UnaryOperator<CatalogItem> change = invocation.getArgument(2);
          return change.apply(item);
        });
    var patch = new CatalogItemPatch(Optional.of("Otra silla"), Optional.empty(), Optional.empty());
    assertThat(new UpdateCatalogItem(company, repository, policy).execute(item.id(), patch).name())
        .isEqualTo("Otra silla");
    var status = new ChangeCatalogItemStatus(company, repository);
    assertThat(status.execute(item.id(), true)).isSameAs(item);
    var inactive = status.execute(item.id(), false);
    assertThat(inactive.active()).isFalse();
    when(repository.update(eq(tenant), eq(item.id()), any()))
        .thenAnswer(invocation -> {
          UnaryOperator<CatalogItem> change = invocation.getArgument(2);
          return change.apply(inactive);
        });
    assertThat(status.execute(item.id(), true)).isEqualTo(item);
  }

  @Test
  void propagatesAConcurrentConflict() {
    when(authorization.membership(CATALOG_WRITE)).thenReturn(Optional.of(tenant));
    when(repository.update(eq(tenant), eq(item.id()), any()))
        .thenThrow(new CatalogItemAccessException(CATALOG_ITEM_UPDATE_CONFLICT));
    var patch = new CatalogItemPatch(Optional.of("Otra silla"), Optional.empty(), Optional.empty());
    assertThatThrownBy(
            () -> new UpdateCatalogItem(company, repository, policy).execute(item.id(), patch))
        .isInstanceOf(CatalogItemAccessException.class)
        .hasMessage(CATALOG_ITEM_UPDATE_CONFLICT.name());
  }

  @Test
  void authorizesAllOperationsBeforeAccessingPersistence() {
    var patch = new CatalogItemPatch(Optional.of("Otra silla"), Optional.empty(), Optional.empty());
    assertThatThrownBy(
            () -> new ListCatalogItems(company, repository).execute(null, null, null, 0, 20))
        .isInstanceOf(CatalogItemAccessException.class);
    assertThatThrownBy(() -> new GetCatalogItem(company, repository).execute(item.id()))
        .isInstanceOf(CatalogItemAccessException.class);
    assertThatThrownBy(
            () -> new UpdateCatalogItem(company, repository, policy).execute(item.id(), patch))
        .isInstanceOf(CatalogItemAccessException.class);
    assertThatThrownBy(
            () -> new ChangeCatalogItemStatus(company, repository).execute(item.id(), false))
        .isInstanceOf(CatalogItemAccessException.class);

    when(authorization.membership(CATALOG_READ)).thenThrow(new CatalogAuthorizationUnavailable());
    when(authorization.membership(CATALOG_WRITE)).thenThrow(new CatalogAuthorizationUnavailable());
    assertThatThrownBy(
            () -> new ListCatalogItems(company, repository).execute(null, null, null, 0, 20))
        .isInstanceOf(CatalogAuthorizationUnavailable.class);
    assertThatThrownBy(
            () -> new ChangeCatalogItemStatus(company, repository).execute(item.id(), false))
        .isInstanceOf(CatalogAuthorizationUnavailable.class);
    verifyNoInteractions(repository);
  }
}
