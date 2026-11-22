package com.sanigest.catalog.infrastructure.persistence.jpa;

import static com.sanigest.catalog.application.exception.ClientAccessException.Reason.CLIENT_NOT_FOUND;
import static com.sanigest.catalog.application.exception.ClientAccessException.Reason.CLIENT_UPDATE_CONFLICT;
import static com.sanigest.catalog.application.exception.ClientCreationException.Reason.DUPLICATE_TAX_ID;

import com.sanigest.catalog.application.dto.ClientPage;
import com.sanigest.catalog.application.exception.ClientAccessException;
import com.sanigest.catalog.application.exception.ClientCreationException;
import com.sanigest.catalog.application.port.out.persistence.ClientRepository;
import com.sanigest.catalog.domain.model.Client;
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
      throw translateIntegrityViolation(failure);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Client> find(UUID companyId, UUID id) {
    return rows.findByCompanyIdAndId(companyId, id).map(ClientRow::toDomain);
  }

  @Override
  @Transactional
  public Client update(UUID companyId, UUID id, UnaryOperator<Client> change) {
    var row =
        rows.findByCompanyIdAndId(companyId, id)
            .orElseThrow(() -> new ClientAccessException(CLIENT_NOT_FOUND));
    var current = row.toDomain();
    var updated = change.apply(current);
    if (updated.equals(current)) {
      return current;
    }

    try {
      if (rows.update(companyId, id, row.revision(), updated.details(), updated.status()) != 1) {
        throw new ClientAccessException(CLIENT_UPDATE_CONFLICT);
      }
      return updated;
    } catch (DataIntegrityViolationException failure) {
      throw translateIntegrityViolation(failure);
    }
  }

  private static RuntimeException translateIntegrityViolation(
      DataIntegrityViolationException failure) {
    for (Throwable cause = failure.getCause(); cause != null; cause = cause.getCause()) {
      if (cause instanceof ConstraintViolationException violation
          && "23505".equals(violation.getSQLState())
          && "uq_clients_company_tax_id".equals(violation.getConstraintName())) {
        return new ClientCreationException(DUPLICATE_TAX_ID);
      }
    }
    return failure;
  }

  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public ClientPage search(UUID companyId, String search, String status, int page, int size) {
    var pattern =
        search == null
            ? ""
            : "%" + search.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    var state = status == null ? "" : status;
    long offset = (long) page * size;
    long total = rows.countMatching(companyId, pattern, state);
    List<ClientPage.Item> content =
        offset >= total
            ? List.of()
            : rows.search(companyId, pattern, state, size, offset).stream()
                .map(row ->
                    new ClientPage.Item(
                        row.getId(), row.getName(), row.getTaxId(), row.getStatus()))
                .toList();
    return new ClientPage(content, page, size, total);
  }
}
