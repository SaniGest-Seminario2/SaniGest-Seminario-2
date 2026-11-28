package com.sanigest.catalog.application.usecase.catalogitem;

import static com.sanigest.catalog.application.port.out.integration.company.CatalogAuthorization.Permission.CATALOG_WRITE;

import com.sanigest.catalog.application.port.out.persistence.catalogitem.CatalogItemRepository;
import com.sanigest.catalog.domain.model.catalogitem.CatalogItem;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class ChangeCatalogItemStatus {
  private final CatalogItemCompany company;
  private final CatalogItemRepository items;

  ChangeCatalogItemStatus(CatalogItemCompany company, CatalogItemRepository items) {
    this.company = company;
    this.items = items;
  }

  @PreAuthorize(
      "T(com.sanigest.companyaccess.security.authentication.AuthenticatedActor).userId().isPresent()")
  public CatalogItem execute(UUID id, boolean active) {
    return items.update(company.require(CATALOG_WRITE), id, item -> item.withActive(active));
  }
}
