package com.sanigest.catalog.application.port.out.persistence;

import com.sanigest.catalog.application.dto.CatalogItemCommercialData;
import com.sanigest.catalog.application.dto.CatalogItemHistory;
import com.sanigest.catalog.application.dto.CatalogItemReferences.Tax;
import com.sanigest.catalog.application.dto.CatalogItemReferences.Unit;
import com.sanigest.catalog.application.dto.CatalogItemSummary;
import com.sanigest.catalog.domain.model.CatalogItemCategory;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface CatalogItemCommercialRepository {
  void initializeCategories(UUID companyId);

  List<CatalogItemCategory> categories(UUID companyId);

  CatalogItemCategory lockCategory(UUID companyId, UUID id, boolean exclusive);

  void insertCategory(CatalogItemCategory category);

  void updateCategory(CatalogItemCategory category);

  boolean activeUnit(String code);

  boolean currentTax(String code, LocalDate date);

  List<Unit> units();

  List<Tax> taxes(LocalDate date);

  Map<UUID, CatalogItemCommercialData> details(UUID companyId, List<UUID> ids, LocalDate date);

  CatalogItemSummary summary(UUID companyId);

  CatalogItemHistory history(UUID companyId, UUID itemId, int page, int size);
}
