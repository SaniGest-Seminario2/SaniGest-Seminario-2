package com.sanigest.catalog.application;

import static com.sanigest.catalog.application.CatalogAuthorization.Permission.CATALOG_READ;
import static com.sanigest.catalog.application.ClientAccessException.Reason.CLIENT_NOT_FOUND;

import com.sanigest.catalog.domain.Client;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class GetClient {
  private final ClientCompany company;
  private final ClientRepository clients;

  GetClient(ClientCompany company, ClientRepository clients) {
    this.company = company;
    this.clients = clients;
  }

  @PreAuthorize("T(com.sanigest.companyaccess.AuthenticatedActor).userId().isPresent()")
  public Client execute(UUID id) {
    return clients
        .find(company.require(CATALOG_READ), id)
        .orElseThrow(() -> new ClientAccessException(CLIENT_NOT_FOUND));
  }
}
