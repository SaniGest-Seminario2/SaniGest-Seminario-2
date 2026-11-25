package com.sanigest.catalog.infrastructure.persistence.jpa;

import static com.sanigest.catalog.application.exception.CatalogItemAccessException.Reason.CATALOG_ITEM_NOT_FOUND;
import static com.sanigest.catalog.application.exception.CatalogItemAccessException.Reason.CATALOG_ITEM_UPDATE_CONFLICT;

import com.sanigest.catalog.application.dto.CatalogItemPage;
import com.sanigest.catalog.application.dto.CatalogItemSearchCriteria;
import com.sanigest.catalog.application.exception.CatalogItemAccessException;
import com.sanigest.catalog.application.exception.DuplicateCatalogItemCode;
import com.sanigest.catalog.application.port.out.persistence.CatalogItemRepository;
import com.sanigest.catalog.domain.enums.CatalogItemType;
import com.sanigest.catalog.domain.model.CatalogItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JpaCatalogItemRepository implements CatalogItemRepository {
  private final CatalogItemRows rows;

  private final CatalogHistoryActor historyActor;

  JpaCatalogItemRepository(CatalogItemRows rows, CatalogHistoryActor historyActor) {
    this.rows = rows;
    this.historyActor = historyActor;
  }

  @Override
  @Transactional
  public void insert(CatalogItem item) {
    historyActor.bind();
    try {
      rows.saveAndFlush(new CatalogItemRow(item));
    } catch (DataIntegrityViolationException failure) {
      throw translate(failure);
    }
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

    historyActor.bind();
    int affected;
    try {
      affected = rows.update(companyId, id, row.revision(), updated);
    } catch (DataIntegrityViolationException failure) {
      throw translate(failure);
    }
    if (affected != 1) {
      throw new CatalogItemAccessException(CATALOG_ITEM_UPDATE_CONFLICT);
    }
    return updated;
  }

  private static RuntimeException translate(DataIntegrityViolationException failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof ConstraintViolationException violation) {
        if ("uq_catalog_items_sku".equals(violation.getConstraintName())) {
          return new DuplicateCatalogItemCode("DUPLICATE_SKU");
        }
        if ("uq_catalog_items_barcode".equals(violation.getConstraintName())) {
          return new DuplicateCatalogItemCode("DUPLICATE_BARCODE");
        }
      }
    }
    return failure;
  }

  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public CatalogItemPage search(
      UUID companyId, CatalogItemType type, String search, Boolean active, int page, int size) {
    return search(
        companyId,
        new CatalogItemSearchCriteria(type, search, active, null, null, null),
        page,
        size);
  }

  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public CatalogItemPage search(
      UUID companyId, CatalogItemSearchCriteria criteria, int page, int size) {
    var search = criteria.search();
    var pattern =
        search == null
            ? ""
            : "%" + search.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    var itemType = criteria.type() == null ? "" : criteria.type().name();
    long offset = (long) page * size;
    long total = rows.countMatching(companyId, itemType, pattern, criteria);
    List<CatalogItem> content =
        offset >= total
            ? List.of()
            : rows.search(companyId, itemType, pattern, criteria, size, offset).stream()
                .map(CatalogItemRow::toDomain)
                .toList();
    return new CatalogItemPage(content, page, size, total);
  }
}
