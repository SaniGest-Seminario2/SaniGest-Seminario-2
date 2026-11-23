package com.sanigest.catalog.infrastructure.persistence.jpa;

import static com.sanigest.catalog.application.exception.CatalogItemAccessException.Reason.CATALOG_ITEM_NOT_FOUND;
import static com.sanigest.catalog.application.exception.CatalogItemAccessException.Reason.CATALOG_ITEM_UPDATE_CONFLICT;

import com.sanigest.catalog.application.dto.CatalogItemPage;
import com.sanigest.catalog.application.exception.CatalogItemAccessException;
import com.sanigest.catalog.application.port.out.persistence.CatalogItemRepository;
import com.sanigest.catalog.domain.enums.CatalogItemType;
import com.sanigest.catalog.domain.model.CatalogItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JpaCatalogItemRepository implements CatalogItemRepository {
  private final CatalogItemRows rows;

  JpaCatalogItemRepository(CatalogItemRows rows) {
    this.rows = rows;
  }

  @Override
  @Transactional
  public void insert(CatalogItem item) {
    rows.save(new CatalogItemRow(item));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<CatalogItem> find(UUID companyId, UUID id) {
    return rows.findByCompanyIdAndId(companyId, id).map(CatalogItemRow::toDomain);
  }

  @Override
  @Transactional
  public CatalogItem update(UUID companyId, UUID id, UnaryOperator<CatalogItem> change) {
    var row =
        rows.findByCompanyIdAndId(companyId, id)
            .orElseThrow(() -> new CatalogItemAccessException(CATALOG_ITEM_NOT_FOUND));
    var current = row.toDomain();
    var updated = change.apply(current);
    if (updated.equals(current)) {
      return current;
    }

    int affected =
        rows.update(
            companyId,
            id,
            row.revision(),
            updated.name(),
            updated.description(),
            updated.price(),
            updated.active());
    if (affected != 1) {
      throw new CatalogItemAccessException(CATALOG_ITEM_UPDATE_CONFLICT);
    }
    return updated;
  }

  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public CatalogItemPage search(
      UUID companyId, CatalogItemType type, String search, Boolean active, int page, int size) {
    var pattern =
        search == null
            ? ""
            : "%" + search.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    var itemType = type == null ? "" : type.name();
    boolean requestedActive = Boolean.TRUE.equals(active);
    long offset = (long) page * size;
    long total = rows.countMatching(companyId, itemType, pattern, active == null, requestedActive);
    List<CatalogItem> content =
        offset >= total
            ? List.of()
            : rows
                .search(companyId, itemType, pattern, active == null, requestedActive, size, offset)
                .stream()
                .map(CatalogItemRow::toDomain)
                .toList();
    return new CatalogItemPage(content, page, size, total);
  }
}
