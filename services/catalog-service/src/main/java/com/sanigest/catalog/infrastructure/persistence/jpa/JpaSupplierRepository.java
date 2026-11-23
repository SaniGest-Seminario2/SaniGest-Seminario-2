package com.sanigest.catalog.infrastructure.persistence.jpa;

import static com.sanigest.catalog.application.exception.SupplierAccessException.Reason.SUPPLIER_NOT_FOUND;
import static com.sanigest.catalog.application.exception.SupplierAccessException.Reason.SUPPLIER_UPDATE_CONFLICT;
import static com.sanigest.catalog.application.exception.SupplierCreationException.Reason.DUPLICATE_TAX_ID;

import com.sanigest.catalog.application.dto.SupplierPage;
import com.sanigest.catalog.application.exception.SupplierAccessException;
import com.sanigest.catalog.application.exception.SupplierCreationException;
import com.sanigest.catalog.application.port.out.persistence.SupplierRepository;
import com.sanigest.catalog.domain.enums.SupplierStatus;
import com.sanigest.catalog.domain.model.Supplier;
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
class JpaSupplierRepository implements SupplierRepository {
  private final SupplierRows rows;

  JpaSupplierRepository(SupplierRows rows) {
    this.rows = rows;
  }

  @Override
  @Transactional
  public void insert(Supplier supplier) {
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

    try {
      if (rows.update(companyId, id, row.revision(), updated.details(), updated.status()) != 1) {
        throw new SupplierAccessException(SUPPLIER_UPDATE_CONFLICT);
      }
      return updated;
    } catch (DataIntegrityViolationException failure) {
      if (isDuplicateTaxId(failure)) {
        throw new SupplierAccessException(SupplierAccessException.Reason.DUPLICATE_TAX_ID);
      }
      throw failure;
    }
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
    var pattern =
        search == null
            ? ""
            : "%" + search.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    var state = status == null ? "" : status.name();
    long offset = (long) page * size;
    long total = rows.countMatching(companyId, pattern, state);
    List<SupplierPage.Item> content =
        offset >= total
            ? List.of()
            : rows.search(companyId, pattern, state, size, offset).stream()
                .map(row ->
                    new SupplierPage.Item(
                        row.getId(),
                        row.getName(),
                        row.getTaxId(),
                        SupplierStatus.valueOf(row.getStatus())))
                .toList();
    return new SupplierPage(content, page, size, total);
  }
}
