package com.sanigest.catalog.application.usecase.client;

import static com.sanigest.catalog.application.exception.ClientCreationException.Reason.FORBIDDEN;

import com.sanigest.catalog.application.exception.ClientCreationException;
import com.sanigest.catalog.application.port.out.integration.CatalogAuthorization;
import com.sanigest.catalog.application.port.out.persistence.ClientRepository;
import com.sanigest.catalog.domain.model.Client;
import com.sanigest.catalog.domain.model.ClientDetails;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class CreateClient {
  private final ClientCompany company;
  private final ClientRepository clients;

  public CreateClient(ClientCompany company, ClientRepository clients) {
    this.company = company;
    this.clients = clients;
  }

  @PreAuthorize("T(com.sanigest.companyaccess.AuthenticatedActor).userId().isPresent()")
  public Client execute(ClientDetails details) {
    var companyId =
        company
            .membership(CatalogAuthorization.Permission.CATALOG_WRITE)
            .orElseThrow(() -> new ClientCreationException(FORBIDDEN));
    var client = Client.register(companyId, details);
    clients.insert(client);
    return client;
  }
}
