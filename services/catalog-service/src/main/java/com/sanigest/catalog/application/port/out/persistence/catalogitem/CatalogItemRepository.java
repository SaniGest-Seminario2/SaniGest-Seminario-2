package com.sanigest.catalog.application.port.out.persistence.catalogitem;

import com.sanigest.catalog.application.dto.catalogitem.CatalogItemPage;
import com.sanigest.catalog.application.dto.catalogitem.CatalogItemSearchCriteria;
import com.sanigest.catalog.domain.enums.catalogitem.CatalogItemType;
import com.sanigest.catalog.domain.model.catalogitem.CatalogItem;
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
