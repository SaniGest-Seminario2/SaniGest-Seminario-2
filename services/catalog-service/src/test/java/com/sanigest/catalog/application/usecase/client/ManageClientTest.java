package com.sanigest.catalog.application.usecase.client;

import static com.sanigest.catalog.application.exception.ClientAccessException.Reason.CLIENT_NOT_FOUND;
import static com.sanigest.catalog.application.exception.ClientAccessException.Reason.FORBIDDEN;
import static com.sanigest.catalog.application.exception.ClientCreationException.Reason.AUTHORIZATION_UNAVAILABLE;
import static com.sanigest.catalog.application.exception.ClientCreationException.Reason.DUPLICATE_TAX_ID;
import static com.sanigest.catalog.application.port.out.integration.CatalogAuthorization.Permission.CATALOG_READ;
import static com.sanigest.catalog.application.port.out.integration.CatalogAuthorization.Permission.CATALOG_WRITE;
import static com.sanigest.catalog.domain.enums.ClientStatus.ACTIVE;
import static com.sanigest.catalog.domain.enums.ClientStatus.INACTIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sanigest.catalog.application.exception.ClientAccessException;
import com.sanigest.catalog.application.exception.ClientCreationException;
import com.sanigest.catalog.application.port.out.integration.CatalogAuthorization;
import com.sanigest.catalog.application.port.out.persistence.ClientRepository;
import com.sanigest.catalog.domain.exception.InvalidClientPatch;
import com.sanigest.catalog.domain.model.Client;
import com.sanigest.catalog.domain.model.ClientDetails;
import com.sanigest.catalog.domain.model.ClientPatch;
import com.sanigest.catalog.domain.model.ClientPatch.Field;
import jakarta.validation.Validation;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

class ManageClientTest {
  private final CatalogAuthorization authorization = mock(CatalogAuthorization.class);
  private final ClientRepository clients = mock(ClientRepository.class);
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
      var update = new UpdateClient(company, clients, factory.getValidator());
      var emailOnly =
          update.execute(client.id(), new ClientPatch(Map.of(Field.EMAIL, "new@example.com")));
      assertThat(emailOnly)
          .isEqualTo(
              new Client(
                  client.id(),
                  companyId,
                  new ClientDetails("Acme", "123", "new@example.com", "809-555-1234", "SD"),
                  ACTIVE));

      var values = new EnumMap<Field, String>(Field.class);
      values.put(Field.NAME, "  New  ");
      values.put(Field.TAX_ID, "\t456-7 ");
      values.put(Field.EMAIL, null);
      values.put(Field.ADDRESS, null);
      assertThat(update.execute(client.id(), new ClientPatch(values)))
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
      var update = new UpdateClient(company, clients, factory.getValidator());
      var invalid =
          Map.of(
              Field.NAME,
              "  ",
              Field.EMAIL,
              "invalid",
              Field.PHONE,
              "abc",
              Field.TAX_ID,
              "a".repeat(65),
              Field.ADDRESS,
              "a".repeat(256));
      invalid.forEach((field, value) ->
          assertThatThrownBy(
                  () -> update.execute(client.id(), new ClientPatch(Map.of(field, value))))
              .isInstanceOf(InvalidClientPatch.class));
      var nullName = new EnumMap<Field, String>(Field.class);
      nullName.put(Field.NAME, null);
      assertThatThrownBy(() -> update.execute(client.id(), new ClientPatch(nullName)))
          .isInstanceOf(InvalidClientPatch.class);
      assertThatThrownBy(() -> new ClientPatch(Map.of())).isInstanceOf(InvalidClientPatch.class);
      verifyNoInteractions(clients);
    }
  }

  @Test
  void propagatesDuplicateTaxId() {
    when(authorization.membership(CATALOG_WRITE)).thenReturn(Optional.of(companyId));
    when(clients.update(eq(companyId), eq(client.id()), any()))
        .thenThrow(new ClientCreationException(DUPLICATE_TAX_ID));
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var update = new UpdateClient(company, clients, factory.getValidator());
      assertThatThrownBy(
              () -> update.execute(client.id(), new ClientPatch(Map.of(Field.TAX_ID, "456"))))
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

  private void allowWrite() {
    when(authorization.membership(CATALOG_WRITE)).thenReturn(Optional.of(companyId));
    when(clients.update(eq(companyId), eq(client.id()), any()))
        .thenAnswer(call -> call.<UnaryOperator<Client>>getArgument(2).apply(client));
  }
}
