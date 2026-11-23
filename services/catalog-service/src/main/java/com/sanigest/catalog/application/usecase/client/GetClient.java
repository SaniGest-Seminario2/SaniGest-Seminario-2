package com.sanigest.catalog.application.usecase.client;

import static com.sanigest.catalog.application.exception.ClientAccessException.Reason.CLIENT_NOT_FOUND;
import static com.sanigest.catalog.application.port.out.integration.CatalogAuthorization.Permission.CATALOG_READ;

import com.sanigest.catalog.application.exception.ClientAccessException;
import com.sanigest.catalog.application.port.out.persistence.ClientRepository;
import com.sanigest.catalog.domain.model.Client;
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
    return execute(id, null);
  }

  @PreAuthorize("T(com.sanigest.companyaccess.AuthenticatedActor).userId().isPresent()")
  public Client execute(UUID id, UUID expectedCompanyId) {
    UUID companyId = company.require(CATALOG_READ);
    if (expectedCompanyId != null && !companyId.equals(expectedCompanyId)) {
      throw new ClientAccessException(ClientAccessException.Reason.FORBIDDEN);
    }
    return clients
        .find(companyId, id)
        .orElseThrow(() -> new ClientAccessException(CLIENT_NOT_FOUND));
  }
}
