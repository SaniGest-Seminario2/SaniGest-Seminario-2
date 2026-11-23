package com.sanigest.catalog.application.usecase.supplier;

import static com.sanigest.catalog.application.exception.SupplierAccessException.Reason.DUPLICATE_TAX_ID;
import static com.sanigest.catalog.application.exception.SupplierAccessException.Reason.FORBIDDEN;
import static com.sanigest.catalog.application.exception.SupplierAccessException.Reason.SUPPLIER_NOT_FOUND;
import static com.sanigest.catalog.application.exception.SupplierAccessException.Reason.SUPPLIER_UPDATE_CONFLICT;
import static com.sanigest.catalog.application.port.out.integration.CatalogAuthorization.Permission.CATALOG_READ;
import static com.sanigest.catalog.application.port.out.integration.CatalogAuthorization.Permission.CATALOG_WRITE;
import static com.sanigest.catalog.domain.enums.SupplierStatus.ACTIVE;
import static com.sanigest.catalog.domain.enums.SupplierStatus.INACTIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sanigest.catalog.application.dto.SupplierPage;
import com.sanigest.catalog.application.exception.CatalogAuthorizationUnavailable;
import com.sanigest.catalog.application.exception.InvalidSupplierQueryException;
import com.sanigest.catalog.application.exception.SupplierAccessException;
import com.sanigest.catalog.application.port.out.integration.CatalogAuthorization;
import com.sanigest.catalog.application.port.out.persistence.SupplierRepository;
import com.sanigest.catalog.domain.enums.SupplierStatus;
import com.sanigest.catalog.domain.exception.InvalidSupplierDetails;
import com.sanigest.catalog.domain.model.Supplier;
import com.sanigest.catalog.domain.model.SupplierDetails;
import com.sanigest.catalog.domain.model.SupplierPatch;
import com.sanigest.catalog.domain.model.SupplierPatch.Field;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ManageSupplierTest {
  private final UUID tenant = UUID.randomUUID();
  private final Supplier original =
      Supplier.register(
          tenant, new SupplierDetails("Office", "123-A", "Laura", "8095550101", "old@example.org"));
  private final CatalogAuthorization authorization = mock(CatalogAuthorization.class);
  private final SupplierRepository repository = mock(SupplierRepository.class);
  private final SupplierCompany company = new SupplierCompany(Optional.of(authorization));
  private final GetSupplier detail = new GetSupplier(company, repository);
  private final ListSuppliers list = new ListSuppliers(company, repository, 100);
  private final UpdateSupplier update = new UpdateSupplier(company, repository);
  private final ChangeSupplierStatus status = new ChangeSupplierStatus(company, repository);

  @ParameterizedTest
  @MethodSource("filters")
  void passesOnlyNormalizedFiltersWithinTheReadTenant(
      String search, String state, String normalized, SupplierStatus parsed) {
    var expected = new SupplierPage(List.of(), 2, 20, 0);
    given(authorization.membership(CATALOG_READ)).willReturn(Optional.of(tenant));
    given(repository.search(tenant, normalized, parsed, 2, 20)).willReturn(expected);

    var result = list.execute(search, state, 2, 20);
    assertThat(result).isSameAs(expected);
    assertThat(result.totalPages()).isZero();
  }

  static Stream<Arguments> filters() {
    return Stream.of(
        Arguments.of(null, null, null, null),
        Arguments.of("  off ", "  ", "off", null),
        Arguments.of("123-", null, "123-", null),
        Arguments.of(null, " ACTIVE ", null, ACTIVE),
        Arguments.of(null, "inactive", null, INACTIVE),
        Arguments.of("  off  ", " inactive ", "off", INACTIVE));
  }

  @Test
  void rejectsInvalidPaginationAndFiltersBeforeQuerying() {
    assertThatThrownBy(() -> list.execute(null, null, -1, 20))
        .isInstanceOf(InvalidSupplierQueryException.class);
    assertThatThrownBy(() -> list.execute(null, null, 0, 0))
        .isInstanceOf(InvalidSupplierQueryException.class);
    assertThatThrownBy(() -> list.execute(null, null, 0, 101))
        .isInstanceOf(InvalidSupplierQueryException.class);
    assertThatThrownBy(() -> list.execute(null, "DELETED", 0, 20))
        .isInstanceOf(InvalidSupplierQueryException.class);
    assertThatThrownBy(() -> list.execute("a\0", null, 0, 20))
        .isInstanceOf(InvalidSupplierQueryException.class);
    verifyNoInteractions(repository);
  }

  @Test
  void resolvesDetailOnlyInTheAuthorizedTenant() {
    given(authorization.membership(CATALOG_READ)).willReturn(Optional.of(tenant));
    given(repository.find(tenant, original.id()))
        .willReturn(Optional.of(original), Optional.empty());
    assertThat(detail.execute(original.id())).isEqualTo(original);
    assertThatThrownBy(() -> detail.execute(original.id()))
        .isInstanceOfSatisfying(
            SupplierAccessException.class,
            failure -> assertThat(failure.reason()).isEqualTo(SUPPLIER_NOT_FOUND));
  }

  @Test
  void deniesEachOperationBeforeCallingPersistence() {
    var patch = new SupplierPatch(Map.of(Field.EMAIL, "new@example.org"));
    assertThatThrownBy(() -> list.execute(null, null, 0, 20)).hasMessage(FORBIDDEN.name());
    assertThatThrownBy(() -> detail.execute(original.id())).hasMessage(FORBIDDEN.name());
    assertThatThrownBy(() -> update.execute(original.id(), patch)).hasMessage(FORBIDDEN.name());
    assertThatThrownBy(() -> status.execute(original.id(), INACTIVE)).hasMessage(FORBIDDEN.name());
    given(authorization.membership(CATALOG_READ)).willThrow(new CatalogAuthorizationUnavailable());
    assertThatThrownBy(() -> detail.execute(original.id()))
        .isInstanceOf(CatalogAuthorizationUnavailable.class);
    verifyNoInteractions(repository);
  }

  @Test
  void mergesEditsAndClearsExplicitNullsWithoutChangingIdentityOrStatus() {
    applyChangesTo(original);
    var edited =
        update.execute(original.id(), new SupplierPatch(Map.of(Field.EMAIL, "new@example.org")));
    assertThat(edited)
        .isEqualTo(
            new Supplier(
                original.id(),
                tenant,
                new SupplierDetails("Office", "123-A", "Laura", "8095550101", "new@example.org"),
                ACTIVE));

    var fields = new EnumMap<Field, String>(Field.class);
    fields.put(Field.NAME, " New Office ");
    fields.put(Field.CONTACT, " New contact ");
    fields.put(Field.EMAIL, null);
    fields.put(Field.TAX_ID, null);
    fields.put(Field.PHONE, null);
    assertThat(update.execute(original.id(), new SupplierPatch(fields)).details())
        .isEqualTo(new SupplierDetails("New Office", null, "New contact", null, null));
    assertThat(
            update
                .execute(original.id(), new SupplierPatch(Map.of(Field.TAX_ID, " 123-B ")))
                .details()
                .taxId())
        .isEqualTo("123-B");
  }

  @ParameterizedTest
  @MethodSource("invalidFields")
  void rejectsInvalidSuppliedValues(Field field, String value) {
    applyChangesTo(original);
    var fields = new EnumMap<Field, String>(Field.class);
    fields.put(field, value);
    assertThatThrownBy(() -> update.execute(original.id(), new SupplierPatch(fields)))
        .isInstanceOf(InvalidSupplierDetails.class);
  }

  static Stream<Arguments> invalidFields() {
    return Stream.of(
        Arguments.of(Field.NAME, null),
        Arguments.of(Field.CONTACT, null),
        Arguments.of(Field.NAME, "x".repeat(161)),
        Arguments.of(Field.CONTACT, "  "),
        Arguments.of(Field.EMAIL, "invalid"),
        Arguments.of(Field.PHONE, "123"),
        Arguments.of(Field.TAX_ID, "x".repeat(65)));
  }

  @Test
  void updatesStatusInBothDirectionsAndReusesTheUnchangedSupplier() {
    applyChangesTo(original);
    assertThat(status.execute(original.id(), ACTIVE)).isSameAs(original);
    var inactive = status.execute(original.id(), INACTIVE);
    assertThat(inactive)
        .isEqualTo(new Supplier(original.id(), tenant, original.details(), INACTIVE));
    applyChangesTo(inactive);
    assertThat(status.execute(original.id(), INACTIVE)).isSameAs(inactive);
    assertThat(status.execute(original.id(), ACTIVE)).isEqualTo(original);
  }

  @Test
  void propagatesDuplicateAndConcurrentUpdateConflictsFromPersistence() {
    given(authorization.membership(CATALOG_WRITE)).willReturn(Optional.of(tenant));
    doThrow(
            new SupplierAccessException(DUPLICATE_TAX_ID),
            new SupplierAccessException(SUPPLIER_UPDATE_CONFLICT))
        .when(repository)
        .update(eq(tenant), eq(original.id()), any());
    assertThatThrownBy(() ->
            update.execute(original.id(), new SupplierPatch(Map.of(Field.TAX_ID, "duplicate"))))
        .hasMessage(DUPLICATE_TAX_ID.name());
    assertThatThrownBy(() -> status.execute(original.id(), INACTIVE))
        .hasMessage(SUPPLIER_UPDATE_CONFLICT.name());
  }

  private void applyChangesTo(Supplier supplier) {
    given(authorization.membership(CATALOG_WRITE)).willReturn(Optional.of(tenant));
    doAnswer(call -> call.<UnaryOperator<Supplier>>getArgument(2).apply(supplier))
        .when(repository)
        .update(eq(tenant), eq(original.id()), any());
  }
}
