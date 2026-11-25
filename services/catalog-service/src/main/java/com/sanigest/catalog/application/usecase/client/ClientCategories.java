package com.sanigest.catalog.application.usecase.client;

import static com.sanigest.catalog.application.exception.ClientAccessException.Reason.CLIENT_NOT_FOUND;
import static com.sanigest.catalog.application.port.out.integration.CatalogAuthorization.Permission.CATALOG_READ;
import static com.sanigest.catalog.application.port.out.integration.CatalogAuthorization.Permission.CATALOG_WRITE;

import com.sanigest.catalog.application.exception.ClientAccessException;
import com.sanigest.catalog.application.exception.DuplicateClientCategory;
import com.sanigest.catalog.application.port.out.persistence.ClientCategoryRepository;
import com.sanigest.catalog.domain.enums.ClientStatus;
import com.sanigest.catalog.domain.model.ClientCategory;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("T(com.sanigest.companyaccess.AuthenticatedActor).userId().isPresent()")
public class ClientCategories {
  private final ClientCompany company;
  private final ClientCategoryRepository categories;

  ClientCategories(ClientCompany company, ClientCategoryRepository categories) {
    this.company = company;
    this.categories = categories;
  }

  @Transactional
  public List<ClientCategory> list() {
    var companyId = company.require(CATALOG_READ);
    categories.initialize(companyId);
    return categories.list(companyId);
  }

  @Transactional
  public ClientCategory create(String code, String name) {
    var companyId = company.require(CATALOG_WRITE);
    var category =
        new ClientCategory(UUID.randomUUID(), companyId, code, name, ClientStatus.ACTIVE);
    categories.initialize(companyId);
    if (!categories.insert(category)) {
      throw new DuplicateClientCategory();
    }
    return category;
  }

  @Transactional
  public ClientCategory update(UUID id, String name, ClientStatus status) {
    var companyId = company.require(CATALOG_WRITE);
    var category =
        categories
            .lockForEdit(companyId, id)
            .orElseThrow(() -> new ClientAccessException(CLIENT_NOT_FOUND));
    var updated = category.revise(name, status);
    categories.update(updated);
    return updated;
  }
}
