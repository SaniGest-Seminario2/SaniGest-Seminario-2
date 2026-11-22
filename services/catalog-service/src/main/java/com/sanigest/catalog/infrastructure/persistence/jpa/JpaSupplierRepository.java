package com.sanigest.catalog.infrastructure.persistence.jpa;

import static com.sanigest.catalog.application.exception.SupplierCreationException.Reason.DUPLICATE_TAX_ID;

import com.sanigest.catalog.application.exception.SupplierCreationException;
import com.sanigest.catalog.application.port.out.persistence.SupplierRepository;
import com.sanigest.catalog.domain.model.Supplier;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
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
      for (Throwable cause = failure.getCause(); cause != null; cause = cause.getCause()) {
        if (cause instanceof ConstraintViolationException violation
            && "23505".equals(violation.getSQLState())
            && "uq_suppliers_company_tax_id".equals(violation.getConstraintName())) {
          throw new SupplierCreationException(DUPLICATE_TAX_ID);
        }
      }
      throw failure;
    }
  }
}
