package com.sanigest.catalog.application.usecase.supplier;

import static com.sanigest.catalog.application.exception.supplier.SupplierAccessException.Reason.FORBIDDEN;

import com.sanigest.catalog.application.exception.security.CatalogAuthorizationUnavailable;
import com.sanigest.catalog.application.exception.supplier.SupplierAccessException;
import com.sanigest.catalog.application.port.out.integration.company.CatalogAuthorization;
import com.sanigest.catalog.application.port.out.integration.company.CatalogAuthorization.Permission;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SupplierCompany {
  private final Optional<CatalogAuthorization> authorization;

  SupplierCompany(Optional<CatalogAuthorization> authorization) {
    this.authorization = authorization;
  }

  UUID require(Permission permission) {
    return authorization
        .orElseThrow(CatalogAuthorizationUnavailable::new)
        .membership(permission)
        .orElseThrow(() -> new SupplierAccessException(FORBIDDEN));
  }
}
