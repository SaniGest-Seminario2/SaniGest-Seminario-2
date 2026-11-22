package com.sanigest.catalog.infrastructure.persistence;

import static com.sanigest.catalog.application.ClientCreationException.Reason.DUPLICATE_TAX_ID;

import com.sanigest.catalog.application.ClientCreationException;
import com.sanigest.catalog.application.ClientPage;
import com.sanigest.catalog.application.ClientRepository;
import com.sanigest.catalog.domain.Client;
import java.util.List;
import java.util.UUID;
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
