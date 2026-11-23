package com.sanigest.catalog.infrastructure.persistence.jpa;

import com.sanigest.catalog.application.port.out.persistence.CatalogItemRepository;
import com.sanigest.catalog.domain.model.CatalogItem;
import org.springframework.stereotype.Repository;

@Repository
class JpaCatalogItemRepository implements CatalogItemRepository {
  private final CatalogItemRows rows;

  JpaCatalogItemRepository(CatalogItemRows rows) {
    this.rows = rows;
  }

  @Override
  public void insert(CatalogItem item) {
    rows.save(new CatalogItemRow(item));
  }
}
