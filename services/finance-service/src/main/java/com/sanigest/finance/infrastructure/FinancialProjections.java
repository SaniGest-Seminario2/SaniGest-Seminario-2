package com.sanigest.finance.infrastructure;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class FinancialProjections {
  private final JdbcClient jdbc;
  private final FinancialSources sources;

  FinancialProjections(JdbcClient jdbc, FinancialSources sources) {
    this.jdbc = jdbc;
    this.sources = sources;
  }

  public String read(UUID company, String token) {
    sources.authorize(company, token);
    return view(company);
  }

  public String rebuild(UUID company, String token) {
    save(company, sources.fetch(company, token));
    return view(company);
  }

  void save(UUID company, FinancialSources.Snapshots snapshots) {
    jdbc.sql(
            """
            INSERT INTO finance.financial_snapshots AS current
                (company_id, sales_as_of, expense_as_of, sales, expense)
            SELECT :company, (sales ->> 'dataAsOf')::timestamptz,
                   (expense ->> 'dataAsOf')::timestamptz, sales, expense
            FROM (SELECT CAST(:sales AS jsonb) AS sales, CAST(:expense AS jsonb) AS expense) source
            ON CONFLICT (company_id) DO UPDATE SET
                sales_as_of = excluded.sales_as_of, expense_as_of = excluded.expense_as_of,
                sales = excluded.sales, expense = excluded.expense
            WHERE excluded.sales_as_of >= current.sales_as_of
              AND excluded.expense_as_of >= current.expense_as_of
            """)
        .param("company", company)
        .param("sales", snapshots.sales())
        .param("expense", snapshots.expense())
        .update();
  }

  String view(UUID company) {
    return jdbc.sql(
            """
            WITH documents AS (
              SELECT * FROM finance.financial_snapshots WHERE company_id = :company
            ), document_totals AS (
              SELECT kind, sum(amount) AS amount, sum(count) AS count FROM documents
              CROSS JOIN LATERAL jsonb_to_recordset((sales -> 'series') || (expense -> 'series'))
                  AS series(kind text, amount numeric, count bigint)
              GROUP BY kind
            ), movements AS (
              SELECT event_id AS id, occurred_at, origin, event_type, amount,
                     (occurred_at AT TIME ZONE 'America/Santo_Domingo')::date AS date,
                     CASE
                       WHEN original_event_id IS NULL THEN direction
                       WHEN direction = 'INFLOW' THEN 'OUTFLOW' ELSE 'INFLOW'
                     END AS original_direction,
                     CASE WHEN original_event_id IS NULL THEN amount ELSE -amount END AS net
              FROM finance.cash_movements
              WHERE company_id = :company AND currency = 'DOP' AND direction IS NOT NULL
            ), cash_series AS (
              SELECT date_trunc('month', date)::date AS month,
                     coalesce(sum(net) FILTER (WHERE original_direction = 'INFLOW'), 0) AS inflow,
                     coalesce(sum(net) FILTER (WHERE original_direction = 'OUTFLOW'), 0) AS outflow
              FROM movements GROUP BY 1
            ), activity AS (
              SELECT value AS item, (value ->> 'date')::date::timestamp
                       AT TIME ZONE 'America/Santo_Domingo' AS occurred_at,
                     value ->> 'id' AS id, value ->> 'kind' AS kind
              FROM documents
              CROSS JOIN LATERAL jsonb_array_elements((sales -> 'activity') || (expense -> 'activity'))
              UNION ALL
              SELECT jsonb_build_object('id', id, 'date', date, 'kind', event_type,
                                        'origin', origin, 'occurredAt', occurred_at, 'amount', amount),
                     occurred_at, id::text, event_type
              FROM (SELECT * FROM movements ORDER BY occurred_at DESC, id DESC LIMIT 20) recent_cash
              ORDER BY occurred_at DESC, id DESC, kind LIMIT 20
            )
            SELECT jsonb_build_object(
                'currency', 'DOP', 'generatedAt', statement_timestamp(),
                'dataAsOf', least(d.sales_as_of, d.expense_as_of),
                'partial', d.company_id IS NULL,
                'missingSources', CASE WHEN d.company_id IS NULL THEN '["sales","expense"]'::jsonb
                                       ELSE '[]'::jsonb END,
                'salesAsOf', d.sales_as_of, 'expenseAsOf', d.expense_as_of,
                'cashLatestEventAt', (SELECT max(occurred_at) FROM movements),
                'receivables', d.sales -> 'balance', 'payables', d.expense -> 'balance',
                'salesSeries', d.sales -> 'series', 'expenseSeries', d.expense -> 'series',
                'documentTotals', CASE WHEN d.company_id IS NOT NULL THEN coalesce(
                    (SELECT jsonb_agg(to_jsonb(t) ORDER BY kind) FROM document_totals t), '[]') END,
                'recentInvoices', d.sales -> 'recentInvoices',
                'cashSeries', coalesce((SELECT jsonb_agg(to_jsonb(s) || jsonb_build_object(
                    'net', inflow - outflow) ORDER BY month) FROM cash_series s), '[]'),
                'cashTotals', (SELECT jsonb_build_object('inflow', coalesce(sum(inflow), 0),
                    'outflow', coalesce(sum(outflow), 0), 'net', coalesce(sum(inflow - outflow), 0))
                    FROM cash_series),
                'activity', coalesce((SELECT jsonb_agg(item ORDER BY occurred_at DESC, id DESC, kind)
                                     FROM activity), '[]')
            )::text
            FROM (VALUES (1)) AS anchor(n) LEFT JOIN documents d ON true
            """)
        .param("company", company)
        .query(String.class)
        .single();
  }
}
