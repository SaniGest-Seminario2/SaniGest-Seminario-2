package com.sanigest.catalog.application.usecase.catalogitem;

import static com.sanigest.catalog.application.port.out.integration.CatalogAuthorization.Permission.CATALOG_WRITE;

import com.sanigest.catalog.application.exception.CatalogAuthorizationUnavailable;
import com.sanigest.catalog.application.exception.CatalogItemCreationForbidden;
import com.sanigest.catalog.application.port.out.integration.CatalogAuthorization;
import com.sanigest.catalog.application.port.out.persistence.CatalogItemRepository;
import com.sanigest.catalog.domain.enums.CatalogItemType;
import com.sanigest.catalog.domain.model.CatalogItem;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class CreateCatalogItem {
  private final Optional<CatalogAuthorization> authorization;
  private final CatalogItemRepository items;

  public CreateCatalogItem(
      Optional<CatalogAuthorization> authorization, CatalogItemRepository items) {
    this.authorization = authorization;
    this.items = items;
  }

  @PreAuthorize("T(com.sanigest.companyaccess.AuthenticatedActor).userId().isPresent()")
  public CatalogItem execute(String name, CatalogItemType type, BigDecimal price) {
    var companyId =
        authorization
            .orElseThrow(CatalogAuthorizationUnavailable::new)
            .membership(CATALOG_WRITE)
            .orElseThrow(CatalogItemCreationForbidden::new);
    var item = CatalogItem.register(companyId, name, type, price);
    items.insert(item);
    return item;
  }
}
