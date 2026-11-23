package com.sanigest.catalog.application.usecase.catalogitem;

import static com.sanigest.catalog.application.exception.CatalogItemAccessException.Reason.CATALOG_ITEM_NOT_FOUND;
import static com.sanigest.catalog.application.port.out.integration.CatalogAuthorization.Permission.CATALOG_READ;

import com.sanigest.catalog.application.exception.CatalogItemAccessException;
import com.sanigest.catalog.application.port.out.persistence.CatalogItemRepository;
import com.sanigest.catalog.domain.model.CatalogItem;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class GetCatalogItem {
  private final CatalogItemCompany company;
  private final CatalogItemRepository items;

  GetCatalogItem(CatalogItemCompany company, CatalogItemRepository items) {
    this.company = company;
    this.items = items;
  }

  @PreAuthorize("T(com.sanigest.companyaccess.AuthenticatedActor).userId().isPresent()")
  public CatalogItem execute(UUID id) {
    return items
        .find(company.require(CATALOG_READ), id)
        .orElseThrow(() -> new CatalogItemAccessException(CATALOG_ITEM_NOT_FOUND));
  }
}
