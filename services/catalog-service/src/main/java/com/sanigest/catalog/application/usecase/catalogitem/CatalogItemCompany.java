package com.sanigest.catalog.application.usecase.catalogitem;

import static com.sanigest.catalog.application.exception.CatalogItemAccessException.Reason.FORBIDDEN;

import com.sanigest.catalog.application.exception.CatalogAuthorizationUnavailable;
import com.sanigest.catalog.application.exception.CatalogItemAccessException;
import com.sanigest.catalog.application.port.out.integration.CatalogAuthorization;
import com.sanigest.catalog.application.port.out.integration.CatalogAuthorization.Permission;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CatalogItemCompany {
  private final Optional<CatalogAuthorization> authorization;

  CatalogItemCompany(Optional<CatalogAuthorization> authorization) {
    this.authorization = authorization;
  }

  UUID require(Permission permission) {
    return authorization
        .orElseThrow(CatalogAuthorizationUnavailable::new)
        .membership(permission)
        .orElseThrow(() -> new CatalogItemAccessException(FORBIDDEN));
  }
}
