package com.sanigest.catalog.application.usecase.client;

import static com.sanigest.catalog.application.port.out.integration.company.CatalogAuthorization.Permission.CATALOG_WRITE;

import com.sanigest.catalog.application.port.out.persistence.client.ClientRepository;
import com.sanigest.catalog.domain.exception.client.InvalidClientPatch;
import com.sanigest.catalog.domain.model.client.Client;
import com.sanigest.catalog.domain.model.client.ClientPatch;
import jakarta.validation.Validator;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateClient {
  private final ClientCompany company;
  private final ClientRepository clients;
  private final ClientProfileRules profiles;
  private final Validator validator;

  UpdateClient(
      ClientCompany company,
      ClientRepository clients,
      Validator validator,
      ClientProfileRules profiles) {
    this.company = company;
    this.clients = clients;
    this.profiles = profiles;
    this.validator = validator;
  }

  @Transactional
  @PreAuthorize(
      "T(com.sanigest.companyaccess.security.authentication.AuthenticatedActor).userId().isPresent()")
  public Client execute(UUID id, ClientPatch patch) {
    var companyId = company.require(CATALOG_WRITE);
    patch.validateFields(validator);
    return clients.update(
        companyId,
        id,
        client -> {
          var updated = client.updateDetails(patch);
          if (updated.details().profile() != null
              && !validator.validate(updated.details()).isEmpty()) {
            throw new InvalidClientPatch();
          }
          profiles.validateSelection(
              companyId, client.details().profile(), updated.details().profile());
          return updated;
        });
  }
}
