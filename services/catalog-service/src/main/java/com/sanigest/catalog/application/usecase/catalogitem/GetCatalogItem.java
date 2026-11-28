package com.sanigest.catalog.application.usecase.catalogitem;

import static com.sanigest.catalog.application.exception.catalogitem.CatalogItemAccessException.Reason.CATALOG_ITEM_NOT_FOUND;
import static com.sanigest.catalog.application.port.out.integration.company.CatalogAuthorization.Permission.CATALOG_READ;

import com.sanigest.catalog.application.exception.catalogitem.CatalogItemAccessException;
import com.sanigest.catalog.application.port.out.persistence.catalogitem.CatalogItemRepository;
import com.sanigest.catalog.domain.model.catalogitem.CatalogItem;
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

  @PreAuthorize(
      "T(com.sanigest.companyaccess.security.authentication.AuthenticatedActor).userId().isPresent()")
  public CatalogItem execute(UUID id) {
    return execute(id, null);
  }

  @PreAuthorize(
      "T(com.sanigest.companyaccess.security.authentication.AuthenticatedActor).userId().isPresent()")
  public CatalogItem execute(UUID id, UUID expectedCompanyId) {
    UUID companyId = company.require(CATALOG_READ);
    if (expectedCompanyId != null && !companyId.equals(expectedCompanyId)) {
      throw new CatalogItemAccessException(CatalogItemAccessException.Reason.FORBIDDEN);
    }
    return items
        .find(companyId, id)
        .orElseThrow(() -> new CatalogItemAccessException(CATALOG_ITEM_NOT_FOUND));
  }
}
