package com.sanigest.catalog.infrastructure.persistence.jpa;

import static com.sanigest.catalog.application.exception.ClientAccessException.Reason.CLIENT_NOT_FOUND;
import static com.sanigest.catalog.application.exception.ClientAccessException.Reason.CLIENT_UPDATE_CONFLICT;
import static com.sanigest.catalog.application.exception.ClientCreationException.Reason.DUPLICATE_TAX_ID;

import com.sanigest.catalog.application.dto.ClientPage;
import com.sanigest.catalog.application.dto.ClientSearchCriteria;
import com.sanigest.catalog.application.exception.ClientAccessException;
import com.sanigest.catalog.application.exception.ClientCreationException;
import com.sanigest.catalog.application.port.out.persistence.ClientRepository;
import com.sanigest.catalog.domain.exception.InvalidClientPatch;
import com.sanigest.catalog.domain.model.Client;
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
class JpaClientRepository implements ClientRepository {
  private final ClientRows rows;

  JpaClientRepository(ClientRows rows) {
    this.rows = rows;
  }

  @Override
  @Transactional
  public void insert(Client client) {
    requireUniqueTaxId(client);
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

    boolean taxChanged =
        !Objects.equals(current.details().taxId(), updated.details().taxId())
            || (current.details().profile() == null && updated.details().profile() != null);
    if (taxChanged) {
      requireUniqueTaxId(updated);
    }
    try {
      if (rows.update(
              companyId,
              id,
              row.revision(),
              updated.details(),
              updated.details().profile(),
              updated.status(),
              row.enforcesTaxKey() || taxChanged)
          != 1) {
        throw new ClientAccessException(CLIENT_UPDATE_CONFLICT);
      }
      return updated;
    } catch (DataIntegrityViolationException failure) {
      throw translateIntegrityViolation(failure);
    }
  }

  private void requireUniqueTaxId(Client client) {
    var taxId = client.details().taxId();
    if (taxId != null && rows.duplicateTaxId(client.companyId(), client.id(), taxId)) {
      throw new ClientCreationException(DUPLICATE_TAX_ID);
    }
  }

  private static RuntimeException translateIntegrityViolation(
      DataIntegrityViolationException failure) {
    for (Throwable cause = failure.getCause(); cause != null; cause = cause.getCause()) {
      if (cause instanceof ConstraintViolationException violation) {
        String constraint = violation.getConstraintName();
        if ("uq_clients_company_tax_id".equals(constraint)
            || "uq_clients_company_tax_key".equals(constraint)) {
          return new ClientCreationException(DUPLICATE_TAX_ID);
        }
        if (constraint != null && constraint.startsWith("fk_client_profile_")) {
          return new InvalidClientPatch();
        }
      }
    }
    return failure;
  }

  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public ClientPage search(UUID companyId, ClientSearchCriteria criteria, int page, int size) {
    var search = criteria.search();
    var pattern =
        search == null
            ? ""
            : "%" + search.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    var state = criteria.status() == null ? "" : criteria.status();
    var type = criteria.clientType() == null ? "" : criteria.clientType().name();
    var terms = criteria.paymentTerms() == null ? "" : criteria.paymentTerms().name();
    var category = criteria.categoryId() == null ? "" : criteria.categoryId().toString();
    var filter = new ClientRows.Filter(pattern, state, type, category, terms);
    long offset = (long) page * size;
    long total = rows.countMatching(companyId, filter);
    List<ClientPage.Item> content =
        offset >= total
            ? List.of()
            : rows.search(companyId, filter, criteria.descending(), size, offset).stream()
                .map(row ->
                    new ClientPage.Item(
                        row.getId(),
                        row.getName(),
                        row.getTaxId(),
                        row.getStatus(),
                        row.getPhone(),
                        row.getClientType(),
                        row.getPaymentTerms(),
                        row.getCategoryId(),
                        row.getCategoryName()))
                .toList();
    return new ClientPage(content, page, size, total);
  }
}
