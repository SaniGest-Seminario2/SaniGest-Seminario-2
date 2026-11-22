package com.sanigest.catalog.application;

import static com.sanigest.catalog.application.ClientAccessException.Reason.FORBIDDEN;
import static com.sanigest.catalog.application.ClientCreationException.Reason.AUTHORIZATION_UNAVAILABLE;

import com.sanigest.catalog.application.CatalogAuthorization.Permission;
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
    return authorization
        .orElseThrow(() -> new ClientCreationException(AUTHORIZATION_UNAVAILABLE))
        .membership(permission)
        .orElseThrow(() -> new ClientAccessException(FORBIDDEN));
  }
}
