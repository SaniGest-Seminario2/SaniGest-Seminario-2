package com.sanigest.catalog.application.usecase.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sanigest.catalog.application.exception.client.ClientCreationException;
import com.sanigest.catalog.application.port.out.integration.company.CatalogAuthorization;
import com.sanigest.catalog.application.port.out.persistence.client.ClientCategoryRepository;
import com.sanigest.catalog.application.port.out.persistence.client.ClientReferenceRepository;
import com.sanigest.catalog.application.port.out.persistence.client.ClientRepository;
import com.sanigest.catalog.domain.enums.client.ClientPaymentTerms;
import com.sanigest.catalog.domain.enums.client.ClientStatus;
import com.sanigest.catalog.domain.enums.client.ClientType;
import com.sanigest.catalog.domain.exception.client.InvalidClientPatch;
import com.sanigest.catalog.domain.model.client.ClientCategory;
import com.sanigest.catalog.domain.model.client.ClientDetails;
import com.sanigest.catalog.domain.model.client.ClientProfile;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class CreateClientTest {
  private final UUID companyId = UUID.randomUUID();
  private final UUID categoryId = UUID.randomUUID();
  private final ClientCategoryRepository categories = mock(ClientCategoryRepository.class);
  private final ClientProfileRules profiles =
      new ClientProfileRules(categories, mock(ClientReferenceRepository.class));
  private final ClientRepository clients = mock(ClientRepository.class);

  @Test
  void createsAnActiveClientInTheCompanyAuthorizedForTheActor() {
    var useCase =
        useCase(permission -> {
          assertThat(permission).isEqualTo(CatalogAuthorization.Permission.CATALOG_WRITE);
          return Optional.of(companyId);
        });

    when(categories.defaultId(companyId)).thenReturn(categoryId);
    when(categories.lockForAssignment(companyId, categoryId))
        .thenReturn(
            Optional.of(
                new ClientCategory(
                    categoryId, companyId, "REGULAR", "Regular", ClientStatus.ACTIVE)));
    var profile =
        new ClientProfile(ClientType.LEGAL, null, "US", null, null, null, null, null, null, null);
    var client =
        useCase.execute(
            new ClientDetails(
                " Taller ", " \tAb-012 ", "a@b.com", "809-555-1234", "Santo Domingo", profile));

    assertThat(client.id()).isNotNull();
    assertThat(client.companyId()).isEqualTo(companyId);
    assertThat(client.status()).isEqualTo(ClientStatus.ACTIVE);
    assertThat(client.details())
        .isEqualTo(
            new ClientDetails(
                "Taller",
                "Ab-012",
                "a@b.com",
                "809-555-1234",
                "Santo Domingo",
                profile.defaults(categoryId)));
    assertThat(client.details().profile().paymentTerms()).isEqualTo(ClientPaymentTerms.CASH);
    assertThat(client.details().taxId()).isEqualTo("Ab-012");
    verify(clients).insert(client);
  }

  @Test
  void rejectsCreationWithoutACommercialProfile() {
    var useCase = useCase(permission -> Optional.of(companyId));
    assertThatThrownBy(() -> useCase.execute(new ClientDetails("First", null, null, null, null)))
        .isInstanceOf(InvalidClientPatch.class);
    verifyNoInteractions(clients);
  }

  @Test
  void rejectsBlankNames() {
    assertThatThrownBy(() -> new ClientDetails(" \t ", null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void deniesActorsWithoutPermission() {
    assertRejected(
        useCase(permission -> Optional.empty()), ClientCreationException.Reason.FORBIDDEN);
  }

  @Test
  void failsClosedWhenTheAuthorityIsUnavailable() {
    var unavailable = ClientCreationException.Reason.AUTHORIZATION_UNAVAILABLE;
    assertRejected(
        new CreateClient(new ClientCompany(Optional.empty()), clients, profiles), unavailable);
    assertRejected(
        useCase(permission -> {
          throw new ClientCreationException(unavailable);
        }),
        unavailable);
  }

  private CreateClient useCase(Function<CatalogAuthorization.Permission, Optional<UUID>> resolve) {
    var authorization = mock(CatalogAuthorization.class);
    when(authorization.membership(CatalogAuthorization.Permission.CATALOG_WRITE))
        .thenAnswer(invocation -> resolve.apply(invocation.getArgument(0)));
    return new CreateClient(new ClientCompany(Optional.of(authorization)), clients, profiles);
  }

  private void assertRejected(CreateClient useCase, ClientCreationException.Reason reason) {
    var details = new ClientDetails("Client", null, null, null, null);
    assertThatThrownBy(() -> useCase.execute(details))
        .isInstanceOfSatisfying(
            ClientCreationException.class,
            failure -> assertThat(failure.reason()).isEqualTo(reason));
    verifyNoInteractions(clients);
  }
}
