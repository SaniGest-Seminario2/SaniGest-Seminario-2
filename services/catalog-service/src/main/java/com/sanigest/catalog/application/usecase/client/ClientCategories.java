package com.sanigest.catalog.application.usecase.client;

import static com.sanigest.catalog.application.port.out.integration.CatalogAuthorization.Permission.CATALOG_READ;

import com.sanigest.catalog.application.port.out.persistence.ClientCategoryRepository;
import com.sanigest.catalog.domain.model.ClientCategory;
import java.util.List;
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
}
