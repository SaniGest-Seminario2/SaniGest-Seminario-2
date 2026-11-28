package com.sanigest.expense.infrastructure.persistence.expense;

import com.sanigest.expense.application.dto.expense.ExpenseRegistration.Document;
import com.sanigest.expense.application.dto.expense.ExpenseRegistration.FinancialStatus;
import com.sanigest.expense.application.dto.expense.ExpenseRegistration.RegistrationKey;
import com.sanigest.expense.application.port.out.persistence.expense.ExpenseStore;
import com.sanigest.security.api.observability.RequestCorrelation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Repository
class JdbcExpenseStore implements ExpenseStore {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  JdbcExpenseStore(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Override
  public Optional<Stored> find(RegistrationKey key) {
    return jdbc
        .query(
            """
            SELECT request_hash, snapshot, inventory_synced
            FROM expense.documents
            WHERE company_id = ? AND actor_id = ? AND request_key = ?
            """,
            (row, index) ->
                new Stored(
                    row.getString("request_hash"),
                    json.readValue(row.getString("snapshot"), Document.class),
                    row.getBoolean("inventory_synced")),
            key.companyId(),
            key.actorId(),
            key.requestKey())
        .stream()
        .findFirst();
  }

  @Override
  @Transactional
  public Stored save(RegistrationKey key, String requestHash, Document document) {
    int created =
        jdbc.update(
            """
            INSERT INTO expense.documents
              (company_id, id, actor_id, request_key, request_hash, supplier_id, document_date, snapshot)
            VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
            ON CONFLICT (company_id, actor_id, request_key) DO NOTHING
            """,
            key.companyId(),
            document.id(),
            key.actorId(),
            key.requestKey(),
            requestHash,
            document.supplierId(),
            document.documentDate(),
            json.writeValueAsString(document));
    if (created == 0) {
      return find(key).orElseThrow();
    }
    if (document.financialStatus() == FinancialStatus.OPEN) {
      jdbc.update(
          """
          INSERT INTO expense.payables (company_id, expense_id, supplier_id, due_date, original_amount)
          VALUES (?, ?, ?, ?, ?)
          """,
          key.companyId(),
          document.id(),
          document.supplierId(),
          document.dueDate(),
          document.total());
    }
    jdbc.update(
        """
        INSERT INTO expense.audit_log
            (id, company_id, actor_id, operation, resource_id, state, correlation_id)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        UUID.randomUUID(),
        key.companyId(),
        key.actorId(),
        document.operationType().name() + "_REGISTERED",
        document.id(),
        document.financialStatus().name(),
        RequestCorrelation.current());
    return new Stored(requestHash, document, false);
  }

  @Override
  public void markInventorySynced(RegistrationKey key, UUID expenseId) {
    jdbc.update(
        """
        UPDATE expense.documents SET inventory_synced = TRUE
        WHERE company_id = ? AND id = ?
        """,
        key.companyId(),
        expenseId);
  }
}
