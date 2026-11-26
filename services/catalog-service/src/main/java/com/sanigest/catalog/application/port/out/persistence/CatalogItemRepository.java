package com.sanigest.catalog.application.port.out.persistence;

import com.sanigest.catalog.application.dto.CatalogItemPage;
import com.sanigest.catalog.application.dto.CatalogItemSearchCriteria;
import com.sanigest.catalog.domain.enums.CatalogItemType;
import com.sanigest.catalog.domain.model.CatalogItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

public interface CatalogItemRepository {
  void insert(CatalogItem item);

  CatalogItemPage search(
      UUID companyId, CatalogItemType type, String search, Boolean active, int page, int size);

  CatalogItemPage search(UUID companyId, CatalogItemSearchCriteria criteria, int page, int size);

  List<CatalogItem> scan(
      UUID companyId, CatalogItemSearchCriteria criteria, UUID afterId, int size);

  Optional<CatalogItem> find(UUID companyId, UUID id);

  CatalogItem update(UUID companyId, UUID id, UnaryOperator<CatalogItem> change);
}
