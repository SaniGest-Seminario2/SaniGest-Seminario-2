package com.sanigest.catalog.application;

import static com.sanigest.catalog.application.CatalogAuthorization.Permission.CATALOG_WRITE;

import com.sanigest.catalog.domain.Client;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class ChangeClientStatus {
  private final ClientCompany company;
  private final ClientRepository clients;

  ChangeClientStatus(ClientCompany company, ClientRepository clients) {
    this.company = company;
    this.clients = clients;
  }

  @PreAuthorize("T(com.sanigest.companyaccess.AuthenticatedActor).userId().isPresent()")
  public Client execute(UUID id, Client.Status status) {
    return clients.update(
        company.require(CATALOG_WRITE), id, client -> client.changeStatus(status));
  }
}
