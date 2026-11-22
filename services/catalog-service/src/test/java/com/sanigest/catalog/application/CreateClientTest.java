package com.sanigest.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sanigest.catalog.domain.Client;
import com.sanigest.catalog.domain.ClientDetails;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreateClientTest {
  private final UUID userId = UUID.randomUUID();
  private final UUID companyId = UUID.randomUUID();
  private final ClientRepository clients = mock(ClientRepository.class);

  @Test
  void createsAnActiveClientInTheCompanyAuthorizedForTheActor() {
    var useCase =
        useCase((actor, token) -> {
          assertThat(actor).isEqualTo(userId);
          assertThat(token).isEqualTo("token");
          return Optional.of(companyId);
        });

    var client =
        useCase.execute(
            userId,
            "token",
            new ClientDetails(
                " Taller ", " \tAb-012 ", "a@b.com", "809-555-1234", "Santo Domingo"));

    assertThat(client.id()).isNotNull();
    assertThat(client.companyId()).isEqualTo(companyId);
    assertThat(client.status()).isEqualTo(Client.Status.ACTIVE);
    assertThat(client.details())
        .isEqualTo(
            new ClientDetails("Taller", "Ab-012", "a@b.com", "809-555-1234", "Santo Domingo"));
    assertThat(client.details().taxId()).isEqualTo("Ab-012");
    verify(clients).insert(client);
  }

  @Test
  void createsDistinctClientsWithOnlyAName() {
    var useCase = useCase((actor, token) -> Optional.of(companyId));
    var first =
        useCase.execute(userId, "token", new ClientDetails("First", null, null, null, null));
    var second =
        useCase.execute(userId, "token", new ClientDetails("Second", " \t ", null, null, null));

    assertThat(first.details().taxId()).isNull();
    assertThat(second.details().taxId()).isNull();
    assertThat(first.id()).isNotEqualTo(second.id());
    verify(clients).insert(first);
    verify(clients).insert(second);
  }

  @Test
  void rejectsBlankNames() {
    assertThatThrownBy(() -> new ClientDetails(" \t ", null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void deniesActorsWithoutPermission() {
    assertRejected(
        useCase((actor, token) -> Optional.empty()), ClientCreationException.Reason.FORBIDDEN);
  }

  @Test
  void failsClosedWhenTheAuthorityIsUnavailable() {
    var unavailable = ClientCreationException.Reason.AUTHORIZATION_UNAVAILABLE;
    assertRejected(new CreateClient(Optional.empty(), clients), unavailable);
    assertRejected(
        useCase((actor, token) -> {
          throw new ClientCreationException(unavailable);
        }),
        unavailable);
  }

  private CreateClient useCase(ClientCreationAuthorization authorization) {
    return new CreateClient(Optional.of(authorization), clients);
  }

  private void assertRejected(CreateClient useCase, ClientCreationException.Reason reason) {
    var details = new ClientDetails("Client", null, null, null, null);
    assertThatThrownBy(() -> useCase.execute(userId, "token", details))
        .isInstanceOfSatisfying(
            ClientCreationException.class,
            failure -> assertThat(failure.reason()).isEqualTo(reason));
    verifyNoInteractions(clients);
  }
}
