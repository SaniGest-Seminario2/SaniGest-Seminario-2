package com.sanigest.catalog.application;

import static com.sanigest.catalog.application.ClientCreationException.Reason.AUTHORIZATION_UNAVAILABLE;
import static com.sanigest.catalog.application.ClientCreationException.Reason.FORBIDDEN;

import com.sanigest.catalog.domain.Client;
import com.sanigest.catalog.domain.ClientDetails;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CreateClient {
  private final Optional<ClientCreationAuthorization> authorization;
  private final ClientRepository clients;

  public CreateClient(
      Optional<ClientCreationAuthorization> authorization, ClientRepository clients) {
    this.authorization = authorization;
    this.clients = clients;
  }

  public Client execute(UUID userId, String accessToken, ClientDetails details) {
    var companyId =
        authorization
            .orElseThrow(() -> new ClientCreationException(AUTHORIZATION_UNAVAILABLE))
            .authorizedCompany(userId, accessToken)
            .orElseThrow(() -> new ClientCreationException(FORBIDDEN));
    var client = Client.register(companyId, details);
    clients.insert(client);
    return client;
  }
}
