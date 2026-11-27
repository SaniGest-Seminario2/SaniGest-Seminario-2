package com.sanigest.sales.infrastructure.persistence;

import com.sanigest.sales.application.FinancialSnapshot;
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
              SELECT id, sale_date AS date, 'SALE' AS kind, total, status
              FROM sales.sales WHERE company_id = :company
              UNION ALL
              SELECT id, sale_date, 'INVOICE', total, status
              FROM sales.internal_invoices WHERE company_id = :company
            ), series AS (
              SELECT date_trunc('month', date)::date AS month, kind,
                     sum(total) AS amount, count(*) AS count
              FROM documents WHERE status <> 'CANCELLED'
              GROUP BY 1, 2
            ), activity AS (
              SELECT * FROM documents ORDER BY date DESC, id DESC, kind LIMIT 20
            ), recent AS (
              SELECT * FROM documents WHERE kind = 'INVOICE' ORDER BY date DESC, id DESC LIMIT 20
            )
            SELECT jsonb_build_object(
                'dataAsOf', statement_timestamp(),
                'balance', (SELECT coalesce(sum(balance), 0) FROM sales.receivables
                            WHERE company_id = :company AND status IN ('OPEN', 'PARTIALLY_PAID')),
                'series', coalesce((SELECT jsonb_agg(to_jsonb(s) ORDER BY month, kind) FROM series s), '[]'),
                'activity', coalesce((SELECT jsonb_agg(to_jsonb(a) ORDER BY date DESC, id DESC, kind)
                                      FROM activity a), '[]'),
                'recentInvoices', coalesce((SELECT jsonb_agg(to_jsonb(r) ORDER BY date DESC, id DESC)
                                            FROM recent r WHERE kind = 'INVOICE'), '[]')
            )::text
            """)
        .param("company", company)
        .query(String.class)
        .single();
  }
}
