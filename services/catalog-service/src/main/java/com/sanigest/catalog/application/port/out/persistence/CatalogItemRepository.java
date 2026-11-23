package com.sanigest.catalog.application.port.out.persistence;

import com.sanigest.catalog.domain.model.CatalogItem;

public interface CatalogItemRepository {
  void insert(CatalogItem item);
}
