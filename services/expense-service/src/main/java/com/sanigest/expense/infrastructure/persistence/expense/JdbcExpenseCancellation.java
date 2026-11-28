package com.sanigest.expense.infrastructure.persistence.expense;

import com.sanigest.expense.application.exception.ExpenseFailure;
import com.sanigest.expense.application.exception.ExpenseFailure.Kind;
import com.sanigest.expense.application.port.in.expense.CancelExpense;
import com.sanigest.security.api.observability.RequestCorrelation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JdbcExpenseCancellation implements CancelExpense {
  private final JdbcTemplate jdbc;

  JdbcExpenseCancellation(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  @Transactional
  public void cancel(UUID company, UUID actor, UUID id, String reason) {
    var document =
        jdbc
            .query(
                """
                SELECT cancelled_at IS NOT NULL, snapshot ->> 'receiptStatus'
                FROM expense.documents WHERE company_id = ? AND id = ? FOR UPDATE
                """,
                (row, index) -> new DocumentState(row.getBoolean(1), row.getString(2)),
                company,
                id)
            .stream()
            .findFirst()
            .orElseThrow(() -> new ExpenseFailure(Kind.NOT_FOUND, "EXPENSE_NOT_FOUND"));
    if (document.cancelled()) {
      return;
    }

    // Payments lock the same payable row before changing its balance.
    var paid =
        jdbc
            .queryForList(
                """
                SELECT paid_amount FROM expense.payables
                WHERE company_id = ? AND expense_id = ? FOR UPDATE
                """,
                BigDecimal.class,
                company,
                id)
            .stream()
            .findFirst()
            .orElse(BigDecimal.ZERO);
    if (paid.signum() > 0) {
      throw new ExpenseFailure(Kind.CONFLICT, "EXPENSE_HAS_PAYMENTS");
    }
    if (!"PENDING".equals(document.receiptStatus())) {
      throw new ExpenseFailure(Kind.CONFLICT, "EXPENSE_HAS_RECEIPT");
    }

    var cancelledAt = java.sql.Timestamp.from(Instant.now());
    jdbc.update(
        """
        UPDATE expense.documents SET cancelled_at = ?, cancellation_reason = ?
        WHERE company_id = ? AND id = ?
        """,
        cancelledAt,
        reason,
        company,
        id);
    jdbc.update(
        "UPDATE expense.payables SET cancelled_at = ? WHERE company_id = ? AND expense_id = ?",
        cancelledAt,
        company,
        id);
    jdbc.update(
        """
        INSERT INTO expense.audit_log
            (id, company_id, actor_id, operation, resource_id, state, correlation_id)
        VALUES (?, ?, ?, 'EXPENSE_CANCELLED', ?, 'CANCELLED', ?)
        """,
        UUID.randomUUID(),
        company,
        actor,
        id,
        RequestCorrelation.current());
  }

  private record DocumentState(boolean cancelled, String receiptStatus) {}
}
