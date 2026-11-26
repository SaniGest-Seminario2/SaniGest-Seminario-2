package com.sanigest.catalog.application.usecase.catalogitem;

import static com.sanigest.catalog.application.exception.CatalogItemAccessException.Reason.CATALOG_ITEM_NOT_FOUND;
import static com.sanigest.catalog.application.port.out.integration.CatalogAuthorization.Permission.CATALOG_READ;
import static com.sanigest.catalog.application.port.out.integration.CatalogAuthorization.Permission.CATALOG_WRITE;

import com.sanigest.catalog.application.dto.CatalogItemCommercialData;
import com.sanigest.catalog.application.dto.CatalogItemHistory;
import com.sanigest.catalog.application.dto.CatalogItemPage;
import com.sanigest.catalog.application.dto.CatalogItemReferences.Tax;
import com.sanigest.catalog.application.dto.CatalogItemReferences.Unit;
import com.sanigest.catalog.application.dto.CatalogItemSummary;
import com.sanigest.catalog.application.exception.CatalogItemAccessException;
import com.sanigest.catalog.application.exception.InvalidCatalogItemQuery;
import com.sanigest.catalog.application.port.out.persistence.CatalogItemCommercialRepository;
import com.sanigest.catalog.application.port.out.persistence.CatalogItemRepository;
import com.sanigest.catalog.domain.model.CatalogItem;
import com.sanigest.catalog.domain.model.CatalogItemCategory;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("T(com.sanigest.companyaccess.AuthenticatedActor).userId().isPresent()")
public class CatalogItemCommercialQueries {
  private final CatalogItemCompany company;
  private final CatalogItemCommercialRepository commercial;
  private final CatalogItemRepository items;
  private final CatalogBusinessDate date;
  private final GetCatalogItem getItem;
  private final ListCatalogItems listItems;
  private final FilterCatalogStock stockFilter;

  CatalogItemCommercialQueries(
      CatalogItemCompany company,
      CatalogItemCommercialRepository commercial,
      CatalogItemRepository items,
      CatalogBusinessDate date,
      GetCatalogItem getItem,
      ListCatalogItems listItems,
      FilterCatalogStock stockFilter) {
    this.company = company;
    this.commercial = commercial;
    this.items = items;
    this.date = date;
    this.getItem = getItem;
    this.listItems = listItems;
    this.stockFilter = stockFilter;
  }

  @Transactional
  public List<CatalogItemCategory> categories() {
    var tenant = company.require(CATALOG_READ);
    commercial.initializeCategories(tenant);
    return commercial.categories(tenant);
  }

  @Transactional
  public CatalogItemCategory createCategory(String code, String name) {
    var tenant = company.require(CATALOG_WRITE);
    commercial.initializeCategories(tenant);
    var category = new CatalogItemCategory(UUID.randomUUID(), tenant, code, name, true);
    commercial.insertCategory(category);
    return category;
  }

  @Transactional
  public CatalogItemCategory updateCategory(UUID id, String name, boolean active) {
    var tenant = company.require(CATALOG_WRITE);
    var current = commercial.lockCategory(tenant, id, true);
    var category = new CatalogItemCategory(id, tenant, current.code(), name, active);
    commercial.updateCategory(category);
    return category;
  }

  public List<Unit> units() {
    company.require(CATALOG_READ);
    return commercial.units();
  }

  public List<Tax> taxes() {
    company.require(CATALOG_READ);
    return commercial.taxes(date.today());
  }

  public CatalogItemSummary summary() {
    return commercial.summary(company.require(CATALOG_READ));
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public CatalogItemHistory history(UUID itemId, int page, int size) {
    var tenant = company.require(CATALOG_READ);
    if (page < 0 || size < 1 || size > ListCatalogItems.MAX_SIZE) {
      throw new InvalidCatalogItemQuery();
    }
    if (items.find(tenant, itemId).isEmpty()) {
      throw new CatalogItemAccessException(CATALOG_ITEM_NOT_FOUND);
    }
    return commercial.history(tenant, itemId, page, size);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public ItemView detail(UUID id, UUID expectedCompanyId) {
    var item = getItem.execute(id, expectedCompanyId);
    return new ItemView(item, details(item));
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public PageView list(
      String type,
      String search,
      String active,
      ListCatalogItems.CommercialFilters filters,
      int page,
      int size,
      FilterCatalogStock.Selection stock) {
    var result =
        stock.requested()
            ? stockFilter.execute(type, search, active, filters, page, size, stock)
            : listItems.execute(type, search, active, filters, page, size);
    return new PageView(result, details(result.content()));
  }

  public record ItemView(CatalogItem item, CatalogItemCommercialData data) {}

  public record PageView(CatalogItemPage page, Map<UUID, CatalogItemCommercialData> data) {}

  /** Enriches only items already returned by an authorized operation. */
  private Map<UUID, CatalogItemCommercialData> details(List<CatalogItem> authorizedItems) {
    if (authorizedItems.isEmpty()) {
      return Map.of();
    }
    return commercial.details(
        authorizedItems.getFirst().companyId(),
        authorizedItems.stream().map(CatalogItem::id).toList(),
        date.today());
  }

  public CatalogItemCommercialData details(CatalogItem authorizedItem) {
    return details(List.of(authorizedItem))
        .getOrDefault(authorizedItem.id(), CatalogItemCommercialData.EMPTY);
  }
}
