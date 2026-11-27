package com.sanigest.expense.infrastructure.persistence;

import com.sanigest.expense.application.FinancialSnapshot;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class PostgresFinancialSnapshot implements FinancialSnapshot {
  private final JdbcClient jdbc;

  PostgresFinancialSnapshot(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public String read(UUID company) {
    return jdbc.sql(
            """
            WITH documents AS (
              SELECT id, document_date AS date, operation_type AS kind, total, status
              FROM expense.document_balances WHERE company_id = :company
            ), series AS (
              SELECT date_trunc('month', date)::date AS month, kind,
                     sum(total) AS amount, count(*) AS count
              FROM documents WHERE status <> 'CANCELLED'
              GROUP BY 1, 2
            ), activity AS (
              SELECT * FROM documents ORDER BY date DESC, id DESC, kind LIMIT 20
            )
            SELECT jsonb_build_object(
                'dataAsOf', statement_timestamp(),
                'balance', (SELECT coalesce(sum(outstanding_amount), 0)
                            FROM expense.payable_balances WHERE company_id = :company),
                'series', coalesce((SELECT jsonb_agg(to_jsonb(s) ORDER BY month, kind) FROM series s), '[]'),
                'activity', coalesce((SELECT jsonb_agg(to_jsonb(a) ORDER BY date DESC, id DESC, kind)
                                      FROM activity a), '[]')
            )::text
            """)
        .param("company", company)
        .query(String.class)
        .single();
  }
}
