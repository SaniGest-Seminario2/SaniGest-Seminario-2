package com.sanigest.catalog.application;

import static com.sanigest.catalog.application.ClientCreationException.Reason.AUTHORIZATION_UNAVAILABLE;
import static com.sanigest.catalog.application.ClientCreationException.Reason.FORBIDDEN;

import com.sanigest.catalog.domain.Client;
import com.sanigest.catalog.domain.ClientDetails;
import java.util.Optional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class CreateClient {
  private final Optional<CatalogAuthorization> authorization;
  private final ClientRepository clients;

  public CreateClient(Optional<CatalogAuthorization> authorization, ClientRepository clients) {
    this.authorization = authorization;
    this.clients = clients;
  }

  @PreAuthorize("T(com.sanigest.companyaccess.AuthenticatedActor).userId().isPresent()")
  public Client execute(ClientDetails details) {
    var companyId =
        authorization
            .orElseThrow(() -> new ClientCreationException(AUTHORIZATION_UNAVAILABLE))
            .membership(CatalogAuthorization.Permission.CATALOG_WRITE)
            .orElseThrow(() -> new ClientCreationException(FORBIDDEN));
    var client = Client.register(companyId, details);
    clients.insert(client);
    return client;
  }
}
