package com.sanigest.catalog.application.usecase.catalogitem;

import static com.sanigest.catalog.application.port.out.integration.company.CatalogAuthorization.Permission.CATALOG_WRITE;

import com.sanigest.catalog.application.exception.catalogitem.CatalogItemCreationForbidden;
import com.sanigest.catalog.application.exception.security.CatalogAuthorizationUnavailable;
import com.sanigest.catalog.application.port.out.integration.company.CatalogAuthorization;
import com.sanigest.catalog.application.port.out.persistence.catalogitem.CatalogItemRepository;
import com.sanigest.catalog.domain.enums.catalogitem.CatalogItemType;
import com.sanigest.catalog.domain.model.catalogitem.CatalogItem;
import com.sanigest.catalog.domain.model.catalogitem.CatalogItemProfile;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateCatalogItem {
  private final Optional<CatalogAuthorization> authorization;
  private final CatalogItemRepository items;
  private final CatalogItemWritePolicy policy;

  public CreateCatalogItem(
      Optional<CatalogAuthorization> authorization,
      CatalogItemRepository items,
      CatalogItemWritePolicy policy) {
    this.authorization = authorization;
    this.items = items;
    this.policy = policy;
  }

  @Transactional
  @PreAuthorize(
      "T(com.sanigest.companyaccess.security.authentication.AuthenticatedActor).userId().isPresent()")
  public CatalogItem execute(
      String name,
      CatalogItemType type,
      BigDecimal price,
      String description,
      CatalogItemProfile profile) {
    var companyId =
        authorization
            .orElseThrow(CatalogAuthorizationUnavailable::new)
            .membership(CATALOG_WRITE)
            .orElseThrow(CatalogItemCreationForbidden::new);
    var item =
        new CatalogItem(
            UUID.randomUUID(), companyId, name, type, price, true, description, profile);
    policy.validate(null, item);
    items.insert(item);
    return item;
  }
}
