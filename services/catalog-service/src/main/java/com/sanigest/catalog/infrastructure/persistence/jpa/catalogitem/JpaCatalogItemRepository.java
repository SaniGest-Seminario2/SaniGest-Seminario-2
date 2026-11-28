package com.sanigest.catalog.infrastructure.persistence.jpa.catalogitem;

import static com.sanigest.catalog.application.exception.catalogitem.CatalogItemAccessException.Reason.CATALOG_ITEM_NOT_FOUND;
import static com.sanigest.catalog.application.exception.catalogitem.CatalogItemAccessException.Reason.CATALOG_ITEM_UPDATE_CONFLICT;

import com.sanigest.catalog.application.dto.catalogitem.CatalogItemPage;
import com.sanigest.catalog.application.dto.catalogitem.CatalogItemSearchCriteria;
import com.sanigest.catalog.application.exception.catalogitem.CatalogItemAccessException;
import com.sanigest.catalog.application.exception.catalogitem.DuplicateCatalogItemCode;
import com.sanigest.catalog.application.port.out.persistence.catalogitem.CatalogItemRepository;
import com.sanigest.catalog.domain.enums.catalogitem.CatalogItemType;
import com.sanigest.catalog.domain.model.catalogitem.CatalogItem;
import com.sanigest.catalog.infrastructure.persistence.jpa.CatalogHistoryActor;
import jakarta.persistence.EntityManager;
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

  private final EntityManager entityManager;

  JpaCatalogItemRepository(
      CatalogItemRows rows, CatalogHistoryActor historyActor, EntityManager entityManager) {
    this.entityManager = entityManager;
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
    if (current.active() != updated.active()) {
      historyActor.statusChanged(
          companyId,
          "CATALOG_ITEM",
          id,
          current.active() ? "ACTIVE" : "INACTIVE",
          updated.active() ? "ACTIVE" : "INACTIVE");
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
    var pattern = pattern(criteria.search());
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

  @Override
  public List<CatalogItem> scan(
      UUID companyId, CatalogItemSearchCriteria criteria, UUID afterId, int size) {
    var type = criteria.type() == null ? "" : criteria.type().name();
    var batch = rows.scan(companyId, type, pattern(criteria.search()), criteria, afterId, size);
    var items = batch.stream().map(CatalogItemRow::toDomain).toList();
    batch.forEach(entityManager::detach);
    return items;
  }

  private static String pattern(String search) {
    return search == null
        ? ""
        : "%" + search.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
  }
}
