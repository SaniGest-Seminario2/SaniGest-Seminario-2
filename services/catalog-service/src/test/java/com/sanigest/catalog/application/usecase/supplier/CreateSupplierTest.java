package com.sanigest.catalog.application.usecase.supplier;

import static com.sanigest.catalog.application.port.out.integration.CatalogAuthorization.Permission.CATALOG_WRITE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sanigest.catalog.application.exception.CatalogAuthorizationUnavailable;
import com.sanigest.catalog.application.exception.SupplierCreationException;
import com.sanigest.catalog.application.port.out.integration.CatalogAuthorization;
import com.sanigest.catalog.application.port.out.persistence.SupplierRepository;
import com.sanigest.catalog.domain.enums.SupplierPaymentTerms;
import com.sanigest.catalog.domain.enums.SupplierStatus;
import com.sanigest.catalog.domain.model.SupplierDetails;
import com.sanigest.catalog.domain.model.SupplierProfile;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreateSupplierTest {
  private final CatalogAuthorization authorization = mock(CatalogAuthorization.class);
  private final SupplierRepository suppliers = mock(SupplierRepository.class);
  private final CreateSupplier create = new CreateSupplier(Optional.of(authorization), suppliers);
  private final SupplierDetails details =
      new SupplierDetails(
          "Supplier",
          null,
          "Contact",
          null,
          null,
          new SupplierProfile(null, null, SupplierPaymentTerms.CASH, null, null, null));

  @Test
  void registersAnActiveSupplierInTheAuthorizedCompanyWithAGeneratedId() {
    var company = UUID.randomUUID();
    when(authorization.membership(CATALOG_WRITE)).thenReturn(Optional.of(company));

    var supplier = create.execute(details);

    assertThat(supplier.id()).isNotNull();
    assertThat(supplier.companyId()).isEqualTo(company);
    assertThat(supplier.status()).isEqualTo(SupplierStatus.ACTIVE);
    assertThat(supplier.details()).isEqualTo(details);
    verify(suppliers).insert(supplier);
  }

  @Test
  void deniesCreationWhenMembershipDoesNotAuthorizeWrite() {
    when(authorization.membership(CATALOG_WRITE)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> create.execute(details))
        .isInstanceOfSatisfying(
            SupplierCreationException.class,
            failure ->
                assertThat(failure.reason()).isEqualTo(SupplierCreationException.Reason.FORBIDDEN));
    verifyNoInteractions(suppliers);
  }

  @Test
  void failsClosedWhenCompanyIsUnavailable() {
    when(authorization.membership(CATALOG_WRITE)).thenThrow(new CatalogAuthorizationUnavailable());

    assertThatThrownBy(() -> create.execute(details))
        .isInstanceOf(CatalogAuthorizationUnavailable.class);
    verifyNoInteractions(suppliers);
  }

  @Test
  void failsClosedWhenNoAuthorizationAdapterIsConfigured() {
    var withoutCompany = new CreateSupplier(Optional.empty(), suppliers);

    assertThatThrownBy(() -> withoutCompany.execute(details))
        .isInstanceOf(CatalogAuthorizationUnavailable.class);
    verifyNoInteractions(suppliers);
  }
}
