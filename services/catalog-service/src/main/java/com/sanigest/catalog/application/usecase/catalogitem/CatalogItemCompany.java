package com.sanigest.catalog.application.usecase.catalogitem;

import static com.sanigest.catalog.application.exception.catalogitem.CatalogItemAccessException.Reason.FORBIDDEN;

import com.sanigest.catalog.application.exception.catalogitem.CatalogItemAccessException;
import com.sanigest.catalog.application.exception.security.CatalogAuthorizationUnavailable;
import com.sanigest.catalog.application.port.out.integration.company.CatalogAuthorization;
import com.sanigest.catalog.application.port.out.integration.company.CatalogAuthorization.Permission;
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
