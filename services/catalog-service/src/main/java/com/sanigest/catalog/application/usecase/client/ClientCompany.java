package com.sanigest.catalog.application.usecase.client;

import static com.sanigest.catalog.application.exception.ClientAccessException.Reason.FORBIDDEN;
import static com.sanigest.catalog.application.exception.ClientCreationException.Reason.AUTHORIZATION_UNAVAILABLE;

import com.sanigest.catalog.application.exception.ClientAccessException;
import com.sanigest.catalog.application.exception.ClientCreationException;
import com.sanigest.catalog.application.port.out.integration.CatalogAuthorization;
import com.sanigest.catalog.application.port.out.integration.CatalogAuthorization.Permission;
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
    return authorization
        .orElseThrow(() -> new ClientCreationException(AUTHORIZATION_UNAVAILABLE))
        .membership(permission);
  }
}
