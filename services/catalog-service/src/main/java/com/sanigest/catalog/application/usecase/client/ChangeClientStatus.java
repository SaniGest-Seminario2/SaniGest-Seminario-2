package com.sanigest.catalog.application.usecase.client;

import static com.sanigest.catalog.application.port.out.integration.company.CatalogAuthorization.Permission.CATALOG_WRITE;

import com.sanigest.catalog.application.port.out.persistence.client.ClientRepository;
import com.sanigest.catalog.domain.enums.client.ClientStatus;
import com.sanigest.catalog.domain.model.client.Client;
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

  @PreAuthorize(
      "T(com.sanigest.companyaccess.security.authentication.AuthenticatedActor).userId().isPresent()")
  public Client execute(UUID id, ClientStatus status) {
    return clients.update(
        company.require(CATALOG_WRITE), id, client -> client.changeStatus(status));
  }
}
