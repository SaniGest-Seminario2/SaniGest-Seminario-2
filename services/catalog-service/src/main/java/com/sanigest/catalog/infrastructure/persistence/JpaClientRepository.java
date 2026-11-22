package com.sanigest.catalog.infrastructure.persistence;

import static com.sanigest.catalog.application.ClientCreationException.Reason.DUPLICATE_TAX_ID;

import com.sanigest.catalog.application.ClientCreationException;
import com.sanigest.catalog.application.ClientRepository;
import com.sanigest.catalog.domain.Client;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JpaClientRepository implements ClientRepository {
  private final ClientRows rows;

  JpaClientRepository(ClientRows rows) {
    this.rows = rows;
  }

  @Override
  @Transactional
  public void insert(Client client) {
    try {
      rows.saveAndFlush(new ClientRow(client));
    } catch (DataIntegrityViolationException failure) {
      for (Throwable cause = failure.getCause(); cause != null; cause = cause.getCause()) {
        if (cause instanceof ConstraintViolationException violation
            && "23505".equals(violation.getSQLState())
            && "uq_clients_company_tax_id".equals(violation.getConstraintName())) {
          throw new ClientCreationException(DUPLICATE_TAX_ID);
        }
      }
      throw failure;
    }
  }
}
