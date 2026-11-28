package com.sanigest.catalog.application.usecase.catalogitem;

import static com.sanigest.catalog.application.port.out.integration.company.CatalogAuthorization.Permission.CATALOG_READ;

import com.sanigest.catalog.application.dto.catalogitem.CatalogItemPage;
import com.sanigest.catalog.application.exception.catalogitem.InvalidCatalogItemQuery;
import com.sanigest.catalog.application.exception.inventory.InventoryLookupFailed;
import com.sanigest.catalog.application.exception.inventory.InventoryLookupFailed.Reason;
import com.sanigest.catalog.application.port.out.integration.inventory.InventoryAvailability;
import com.sanigest.catalog.application.port.out.integration.inventory.InventoryAvailability.State;
import com.sanigest.catalog.application.port.out.persistence.catalogitem.CatalogItemRepository;
import com.sanigest.catalog.domain.enums.catalogitem.CatalogItemType;
import com.sanigest.catalog.domain.model.catalogitem.CatalogItem;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class FilterCatalogStock {
  private static final int BATCH_SIZE = 100;
  private static final long BUDGET = Duration.ofMillis(1500).toNanos();
  private final CatalogItemCompany company;
  private final CatalogItemRepository items;
  private final InventoryAvailability inventory;

  FilterCatalogStock(
      CatalogItemCompany company, CatalogItemRepository items, InventoryAvailability inventory) {
    this.company = company;
    this.items = items;
    this.inventory = inventory;
  }

  CatalogItemPage execute(
      String type,
      String search,
      String active,
      ListCatalogItems.CommercialFilters filters,
      int page,
      int size,
      Selection stock) {
    validate(search, page, size, stock);
    var started = System.nanoTime();
    var tenant = company.require(CATALOG_READ);
    var term = search == null || search.isBlank() ? null : search.strip();
    var criteria = ListCatalogItems.criteria(type, term, active, filters);
    var content = new ArrayList<CatalogItem>(size);
    long offset = (long) page * size;
    long total = 0;
    UUID cursor = null;
    while (true) {
      checkBudget(started);
      var batch = items.scan(tenant, criteria, cursor, BATCH_SIZE);
      if (batch.isEmpty()) {
        if (cursor == null) {
          inventory.verifyWarehouse(tenant, stock.warehouseId());
        }
        break;
      }
      for (var item : matching(tenant, stock, batch)) {
        if (total >= offset && content.size() < size) {
          content.add(item);
        }
        total++;
      }
      if (batch.size() < BATCH_SIZE) {
        break;
      }
      cursor = batch.getLast().id();
    }
    checkBudget(started);
    return new CatalogItemPage(List.copyOf(content), page, size, total);
  }

  public record Selection(UUID warehouseId, State availability) {
    boolean requested() {
      return warehouseId != null || availability != null;
    }
  }

  private List<CatalogItem> matching(UUID tenant, Selection stock, List<CatalogItem> batch) {
    var ids = batch.stream().map(CatalogItem::id).toList();
    var positions = inventory.positions(tenant, stock.warehouseId(), ids);
    return batch.stream()
        .filter(item ->
            stock.availability()
                == (item.type() == CatalogItemType.SERVICE
                    ? State.NOT_APPLICABLE
                    : positions.getOrDefault(item.id(), State.NOT_TRACKED)))
        .toList();
  }

  private static void validate(String search, int page, int size, Selection stock) {
    if (stock.warehouseId() == null
        || stock.availability() == null
        || page < 0
        || size < 1
        || size > ListCatalogItems.MAX_SIZE
        || (search != null && search.indexOf(0) >= 0)) {
      throw new InvalidCatalogItemQuery();
    }
  }

  private static void checkBudget(long started) {
    if (System.nanoTime() - started >= BUDGET) {
      throw new InventoryLookupFailed(Reason.UNAVAILABLE);
    }
  }
}
