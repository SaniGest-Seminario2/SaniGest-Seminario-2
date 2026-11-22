package com.sanigest.catalog.application.usecase.client;

import static com.sanigest.catalog.application.port.out.integration.CatalogAuthorization.Permission.CATALOG_WRITE;

import com.sanigest.catalog.application.port.out.persistence.ClientRepository;
import com.sanigest.catalog.domain.exception.InvalidClientPatch;
import com.sanigest.catalog.domain.model.Client;
import com.sanigest.catalog.domain.model.ClientDetails;
import com.sanigest.catalog.domain.model.ClientPatch;
import jakarta.validation.Validator;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class UpdateClient {
  private final ClientCompany company;
  private final ClientRepository clients;
  private final Validator validator;

  UpdateClient(ClientCompany company, ClientRepository clients, Validator validator) {
    this.company = company;
    this.clients = clients;
    this.validator = validator;
  }

  @PreAuthorize("T(com.sanigest.companyaccess.AuthenticatedActor).userId().isPresent()")
  public Client execute(UUID id, ClientPatch patch) {
    var companyId = company.require(CATALOG_WRITE);
    patch
        .values()
        .forEach((field, value) -> {
          if ((value != null && value.indexOf(0) >= 0)
              || !validator.validateValue(ClientDetails.class, field.property(), value).isEmpty()) {
            throw new InvalidClientPatch();
          }
        });
    return clients.update(companyId, id, client -> client.updateDetails(patch));
  }
}
