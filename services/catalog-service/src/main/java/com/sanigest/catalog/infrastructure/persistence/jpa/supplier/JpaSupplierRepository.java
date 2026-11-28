package com.sanigest.catalog.infrastructure.persistence.jpa.supplier;

import static com.sanigest.catalog.application.exception.supplier.SupplierAccessException.Reason.SUPPLIER_NOT_FOUND;
import static com.sanigest.catalog.application.exception.supplier.SupplierAccessException.Reason.SUPPLIER_UPDATE_CONFLICT;
import static com.sanigest.catalog.application.exception.supplier.SupplierCreationException.Reason.DUPLICATE_TAX_ID;

import com.sanigest.catalog.application.dto.supplier.SupplierPage;
import com.sanigest.catalog.application.dto.supplier.SupplierSearchCriteria;
import com.sanigest.catalog.application.exception.supplier.SupplierAccessException;
import com.sanigest.catalog.application.exception.supplier.SupplierCommercialException;
import com.sanigest.catalog.application.exception.supplier.SupplierCreationException;
import com.sanigest.catalog.application.port.out.persistence.supplier.SupplierCategoryRepository;
import com.sanigest.catalog.application.port.out.persistence.supplier.SupplierRepository;
import com.sanigest.catalog.domain.enums.supplier.SupplierStatus;
import com.sanigest.catalog.domain.model.supplier.Supplier;
import com.sanigest.catalog.infrastructure.persistence.jpa.CatalogHistoryActor;
import com.sanigest.catalog.infrastructure.persistence.jpa.LikePattern;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JpaSupplierRepository implements SupplierRepository {
  private final SupplierRows rows;

  private final SupplierCategoryRepository categories;

  private final CatalogHistoryActor historyActor;

  JpaSupplierRepository(
      SupplierRows rows, SupplierCategoryRepository categories, CatalogHistoryActor historyActor) {
    this.categories = categories;
    this.rows = rows;
    this.historyActor = historyActor;
  }

  @Override
  @Transactional
  public void insert(Supplier supplier) {
    requireCategory(supplier.companyId(), supplier.details().profile().categoryId());
    if (hasDomesticDuplicate(supplier)) {
      throw new SupplierCreationException(DUPLICATE_TAX_ID);
    }
    try {
      rows.saveAndFlush(new SupplierRow(supplier));
    } catch (DataIntegrityViolationException failure) {
      if (isDuplicateTaxId(failure)) {
        throw new SupplierCreationException(DUPLICATE_TAX_ID);
      }
      throw failure;
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Supplier> find(UUID companyId, UUID id) {
    return rows.findByCompanyIdAndId(companyId, id).map(SupplierRow::toDomain);
  }

  @Override
  @Transactional
  public Supplier update(UUID companyId, UUID id, UnaryOperator<Supplier> change) {
    var row =
        rows.findByCompanyIdAndId(companyId, id)
            .orElseThrow(() -> new SupplierAccessException(SUPPLIER_NOT_FOUND));
    var current = row.toDomain();
    var updated = change.apply(current);
    if (updated.equals(current)) {
      return current;
    }

    if (!Objects.equals(
        current.details().profile().categoryId(), updated.details().profile().categoryId())) {
      requireCategory(companyId, updated.details().profile().categoryId());
    }
    if (!Objects.equals(current.details().taxId(), updated.details().taxId())
        && hasDomesticDuplicate(updated)) {
      throw new SupplierAccessException(SupplierAccessException.Reason.DUPLICATE_TAX_ID);
    }
    try {
      historyActor.bind();
      if (rows.update(companyId, id, row.revision(), updated.details(), updated.status()) != 1) {
        throw new SupplierAccessException(SUPPLIER_UPDATE_CONFLICT);
      }
      if (current.details().profile().supplyType() != updated.details().profile().supplyType()
          && rows.hasIncompatibleItems(
              companyId,
              id,
              updated.details().profile().supplyType() == null
                  ? ""
                  : updated.details().profile().supplyType().name())) {
        throw new SupplierCommercialException(
            SupplierCommercialException.Reason.INCOMPATIBLE_SUPPLIER_ITEM);
      }
      historyActor.statusChanged(
          companyId, "SUPPLIER", id, current.status().name(), updated.status().name());
      return updated;
    } catch (DataIntegrityViolationException failure) {
      if (isDuplicateTaxId(failure)) {
        throw new SupplierAccessException(SupplierAccessException.Reason.DUPLICATE_TAX_ID);
      }
      throw failure;
    }
  }

  private void requireCategory(UUID companyId, UUID categoryId) {
    if (categoryId != null
        && categories
            .lockForAssignment(companyId, categoryId)
            .filter(category -> category.status() == SupplierStatus.ACTIVE)
            .isEmpty()) {
      throw new SupplierCommercialException(
          SupplierCommercialException.Reason.INVALID_SUPPLIER_CATEGORY);
    }
  }

  private boolean hasDomesticDuplicate(Supplier supplier) {
    var taxId = supplier.details().taxId();
    return taxId != null
        && taxId.matches("[0-9]{9}|[0-9]{11}")
        && rows.hasDomesticDuplicate(supplier.companyId(), supplier.id(), taxId);
  }

  private static boolean isDuplicateTaxId(DataIntegrityViolationException failure) {
    for (Throwable cause = failure.getCause(); cause != null; cause = cause.getCause()) {
      if (cause instanceof ConstraintViolationException violation
          && "23505".equals(violation.getSQLState())
          && "uq_suppliers_company_tax_id".equals(violation.getConstraintName())) {
        return true;
      }
    }
    return false;
  }

  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public SupplierPage search(
      UUID companyId, String search, SupplierStatus status, int page, int size) {
    return search(
        companyId, new SupplierSearchCriteria(search, status, null, null, null, false), page, size);
  }

  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public SupplierPage search(UUID companyId, SupplierSearchCriteria filter, int page, int size) {
    var query =
        new SupplierRows.SearchFilter(
            LikePattern.literalContains(filter.search()),
            enumName(filter.status()),
            enumName(filter.supplyType()),
            enumName(filter.paymentTerms()),
            filter.categoryId(),
            filter.descending());
    long offset = (long) page * size;
    long total = rows.countMatching(companyId, query);
    List<SupplierPage.Item> content =
        offset >= total
            ? List.of()
            : rows.search(companyId, query, size, offset).stream()
                .map(row ->
                    new SupplierPage.Item(
                        row.getId(),
                        row.getName(),
                        row.getTaxId(),
                        SupplierStatus.valueOf(row.getStatus()),
                        row.getContact(),
                        row.getPhone(),
                        row.getSupplyType(),
                        row.getPaymentTerms(),
                        row.getCategoryId()))
                .toList();
    return new SupplierPage(content, page, size, total);
  }

  private static String enumName(Enum<?> value) {
    return value == null ? "" : value.name();
  }
}
