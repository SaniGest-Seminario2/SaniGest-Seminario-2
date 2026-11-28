package com.sanigest.catalog.application.usecase.catalogitem;

import static com.sanigest.catalog.application.port.out.integration.company.CatalogAuthorization.Permission.CATALOG_WRITE;

import com.sanigest.catalog.application.port.out.persistence.catalogitem.CatalogItemRepository;
import com.sanigest.catalog.domain.model.catalogitem.CatalogItem;
import com.sanigest.catalog.domain.model.catalogitem.CatalogItemPatch;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateCatalogItem {
  private final CatalogItemCompany company;
  private final CatalogItemRepository items;
  private final CatalogItemWritePolicy policy;

  UpdateCatalogItem(
      CatalogItemCompany company, CatalogItemRepository items, CatalogItemWritePolicy policy) {
    this.company = company;
    this.items = items;
    this.policy = policy;
  }

  @Transactional
  @PreAuthorize(
      "T(com.sanigest.companyaccess.security.authentication.AuthenticatedActor).userId().isPresent()")
  public CatalogItem execute(UUID id, CatalogItemPatch patch) {
    return items.update(
        company.require(CATALOG_WRITE),
        id,
        item -> {
          var updated = item.apply(patch);
          policy.validate(item, updated);
          return updated;
        });
  }
}
