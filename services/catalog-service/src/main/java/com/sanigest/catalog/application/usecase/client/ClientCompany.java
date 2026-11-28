package com.sanigest.catalog.application.usecase.client;

import static com.sanigest.catalog.application.exception.client.ClientAccessException.Reason.FORBIDDEN;
import static com.sanigest.catalog.application.exception.client.ClientCreationException.Reason.AUTHORIZATION_UNAVAILABLE;

import com.sanigest.catalog.application.exception.client.ClientAccessException;
import com.sanigest.catalog.application.exception.client.ClientCreationException;
import com.sanigest.catalog.application.exception.security.CatalogAuthorizationUnavailable;
import com.sanigest.catalog.application.port.out.integration.company.CatalogAuthorization;
import com.sanigest.catalog.application.port.out.integration.company.CatalogAuthorization.Permission;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ClientCompany {
  private final Optional<CatalogAuthorization> authorization;

  ClientCompany(Optional<CatalogAuthorization> authorization) {
    this.authorization = authorization;
  }

  UUID require(Permission permission) {
    return membership(permission).orElseThrow(() -> new ClientAccessException(FORBIDDEN));
  }

  Optional<UUID> membership(Permission permission) {
    try {
      return authorization.orElseThrow(CatalogAuthorizationUnavailable::new).membership(permission);
    } catch (CatalogAuthorizationUnavailable failure) {
      throw new ClientCreationException(AUTHORIZATION_UNAVAILABLE);
    }
  }
}
