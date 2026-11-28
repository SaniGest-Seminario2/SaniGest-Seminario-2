package com.sanigest.catalog.application.usecase.client;

import static com.sanigest.catalog.application.exception.client.ClientAccessException.Reason.CLIENT_NOT_FOUND;
import static com.sanigest.catalog.application.exception.client.ClientAccessException.Reason.FORBIDDEN;
import static com.sanigest.catalog.application.exception.client.ClientCreationException.Reason.AUTHORIZATION_UNAVAILABLE;
import static com.sanigest.catalog.application.exception.client.ClientCreationException.Reason.DUPLICATE_TAX_ID;
import static com.sanigest.catalog.application.port.out.integration.company.CatalogAuthorization.Permission.CATALOG_READ;
import static com.sanigest.catalog.application.port.out.integration.company.CatalogAuthorization.Permission.CATALOG_WRITE;
import static com.sanigest.catalog.domain.enums.client.ClientStatus.ACTIVE;
import static com.sanigest.catalog.domain.enums.client.ClientStatus.INACTIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sanigest.catalog.api.dto.client.ClientPatchRequest;
import com.sanigest.catalog.application.exception.client.ClientAccessException;
import com.sanigest.catalog.application.exception.client.ClientCreationException;
import com.sanigest.catalog.application.port.out.integration.company.CatalogAuthorization;
import com.sanigest.catalog.application.port.out.persistence.client.ClientCategoryRepository;
import com.sanigest.catalog.application.port.out.persistence.client.ClientReferenceRepository;
import com.sanigest.catalog.application.port.out.persistence.client.ClientRepository;
import com.sanigest.catalog.domain.exception.client.InvalidClientPatch;
import com.sanigest.catalog.domain.model.client.Client;
import com.sanigest.catalog.domain.model.client.ClientDetails;
import com.sanigest.catalog.domain.model.client.ClientPatch;
import jakarta.validation.Validation;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ManageClientTest {
  private final CatalogAuthorization authorization = mock(CatalogAuthorization.class);
  private final ClientRepository clients = mock(ClientRepository.class);
  private final ClientProfileRules profiles =
      new ClientProfileRules(
          mock(ClientCategoryRepository.class), mock(ClientReferenceRepository.class));
  private final ClientCompany company = new ClientCompany(Optional.of(authorization));
  private final UUID companyId = UUID.randomUUID();
  private final Client client =
      Client.register(
          companyId, new ClientDetails("Acme", "123", "old@example.com", "809-555-1234", "SD"));

  @Test
  void readsOnlyWithinTheAuthorizedCompany() {
    when(authorization.membership(CATALOG_READ)).thenReturn(Optional.of(companyId));
    when(clients.find(companyId, client.id())).thenReturn(Optional.of(client));
    var get = new GetClient(company, clients);
    assertThat(get.execute(client.id())).isEqualTo(client);
    assertThatThrownBy(() -> get.execute(UUID.randomUUID()))
        .isInstanceOfSatisfying(
            ClientAccessException.class,
            failure -> assertThat(failure.reason()).isEqualTo(CLIENT_NOT_FOUND));

    when(authorization.membership(CATALOG_READ)).thenReturn(Optional.of(UUID.randomUUID()));
    assertThatThrownBy(() -> get.execute(client.id()))
        .isInstanceOfSatisfying(
            ClientAccessException.class,
            failure -> assertThat(failure.reason()).isEqualTo(CLIENT_NOT_FOUND));
  }

  @Test
  void appliesPartialChangesAndDistinguishesNullFromOmission() {
    allowWrite();
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var update = new UpdateClient(company, clients, factory.getValidator(), profiles);
      var emailOnly = update.execute(client.id(), patch(Map.of("email", "new@example.com")));
      assertThat(emailOnly)
          .isEqualTo(
              new Client(
                  client.id(),
                  companyId,
                  new ClientDetails("Acme", "123", "new@example.com", "809-555-1234", "SD"),
                  ACTIVE));

      var values = new java.util.HashMap<String, String>();
      values.put("name", "  New  ");
      values.put("taxId", "\t456-7 ");
      values.put("email", null);
      values.put("address", null);
      assertThat(update.execute(client.id(), patch(values)))
          .isEqualTo(
              new Client(
                  client.id(),
                  companyId,
                  new ClientDetails("New", "456-7", null, "809-555-1234", null),
                  ACTIVE));
    }
  }

  @Test
  void rejectsInvalidEditsBeforeTouchingPersistence() {
    when(authorization.membership(CATALOG_WRITE)).thenReturn(Optional.of(companyId));
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var update = new UpdateClient(company, clients, factory.getValidator(), profiles);
      var invalid =
          Map.of(
              "name",
              "  ",
              "email",
              "invalid",
              "phone",
              "abc",
              "taxId",
              "a".repeat(65),
              "address",
              "a".repeat(256));
      invalid.forEach((field, value) ->
          assertThatThrownBy(() -> update.execute(client.id(), patch(Map.of(field, value))))
              .isInstanceOf(InvalidClientPatch.class));
      var nullName = new java.util.HashMap<String, String>();
      nullName.put("name", null);
      assertThatThrownBy(() -> update.execute(client.id(), patch(nullName)))
          .isInstanceOf(InvalidClientPatch.class);
      assertThatThrownBy(() -> patch(Map.of())).isInstanceOf(InvalidClientPatch.class);
      verifyNoInteractions(clients);
    }
  }

  @Test
  void propagatesDuplicateTaxId() {
    when(authorization.membership(CATALOG_WRITE)).thenReturn(Optional.of(companyId));
    when(clients.update(eq(companyId), eq(client.id()), any()))
        .thenThrow(new ClientCreationException(DUPLICATE_TAX_ID));
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var update = new UpdateClient(company, clients, factory.getValidator(), profiles);
      assertThatThrownBy(() -> update.execute(client.id(), patch(Map.of("taxId", "456"))))
          .isInstanceOfSatisfying(
              ClientCreationException.class,
              failure -> assertThat(failure.reason()).isEqualTo(DUPLICATE_TAX_ID));
    }
  }

  @Test
  void changesStatusAndPreservesIdentityAndDetails() {
    allowWrite();
    var change = new ChangeClientStatus(company, clients);
    var inactive = change.execute(client.id(), INACTIVE);
    assertThat(inactive).isEqualTo(new Client(client.id(), companyId, client.details(), INACTIVE));
    assertThat(inactive.changeStatus(INACTIVE)).isSameAs(inactive);
    assertThat(inactive.changeStatus(ACTIVE)).isEqualTo(client);
    assertThatThrownBy(() -> change.execute(client.id(), null))
        .isInstanceOf(InvalidClientPatch.class);
  }

  @Test
  void failsClosedBeforeChangingAnything() {
    var change = new ChangeClientStatus(company, clients);
    assertThatThrownBy(() -> change.execute(client.id(), INACTIVE))
        .isInstanceOfSatisfying(
            ClientAccessException.class,
            failure -> assertThat(failure.reason()).isEqualTo(FORBIDDEN));
    when(authorization.membership(CATALOG_WRITE))
        .thenThrow(new ClientCreationException(AUTHORIZATION_UNAVAILABLE));
    assertThatThrownBy(() -> change.execute(client.id(), INACTIVE))
        .isInstanceOfSatisfying(
            ClientCreationException.class,
            failure -> assertThat(failure.reason()).isEqualTo(AUTHORIZATION_UNAVAILABLE));
    verifyNoInteractions(clients);
  }

  private static ClientPatch patch(Map<String, String> values) {
    return ClientPatchRequest.details(JsonMapper.shared().valueToTree(values));
  }

  private void allowWrite() {
    when(authorization.membership(CATALOG_WRITE)).thenReturn(Optional.of(companyId));
    when(clients.update(eq(companyId), eq(client.id()), any()))
        .thenAnswer(call -> call.<UnaryOperator<Client>>getArgument(2).apply(client));
  }
}
