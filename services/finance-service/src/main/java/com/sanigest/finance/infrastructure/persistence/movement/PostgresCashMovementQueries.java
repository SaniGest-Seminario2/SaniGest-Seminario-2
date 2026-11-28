package com.sanigest.finance.infrastructure.persistence.movement;

import com.sanigest.finance.application.port.in.movement.CashMovementQueries;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Repository
class PostgresCashMovementQueries implements CashMovementQueries {
  private static final String FILTERED =
      """
      WITH movements AS (
          SELECT COALESCE(op.id, m.event_id) AS id,
                 COALESCE(op.id, m.source_id) AS reference_id,
                 COALESCE(op.details->>'bankReference', m.reference) AS reference,
                 (m.occurred_at AT TIME ZONE 'America/Santo_Domingo')::date AS date,
                 m.occurred_at, op.details->>'concept' AS concept,
                 CASE m.origin WHEN 'COBRO' THEN 'SALES' WHEN 'PAGO' THEN 'SUPPLIES'
                     ELSE op.details->>'category' END AS category,
                 CASE WHEN m.method IN ('CREDIT_CARD', 'DEBIT_CARD') THEN 'CARD'
                     ELSE m.method END AS method,
                 m.amount, m.currency,
                 CASE WHEN op.id IS NOT NULL THEN m.event_type
                      WHEN m.direction = 'INFLOW' THEN 'INCOME'
                      WHEN m.direction = 'OUTFLOW' THEN 'EXPENSE' END AS type,
                 CASE m.origin WHEN 'COBRO' THEN 'RECEIVABLE_PAYMENT'
                      WHEN 'PAGO' THEN 'PAYABLE_PAYMENT' WHEN 'MANUAL' THEN 'MANUAL'
                      WHEN 'CAJA' THEN 'CASH_SESSION' WHEN 'CASH_SESSION' THEN 'CASH_SESSION'
                      ELSE
                        CASE WHEN m.origin IS NULL AND m.shift_id IS NOT NULL
                             THEN 'CASH_SESSION' END END AS origin,
                 m.account_id,
                 (op.details->>'destinationAccountId')::uuid AS destination_account_id,
                 op.details->>'personOrEntity' AS person_or_entity,
                 COALESCE(op.details->>'observations', m.observations) AS observations,
                 COALESCE(m.document_id, CASE WHEN op.document_type IS NOT NULL THEN op.id END)
                     AS document_id,
                 CASE WHEN op.document_type IS NOT NULL
                      THEN '/api/v1/cash-movements/' || op.id || '/attachment'
                      WHEN m.origin = 'COBRO' THEN '/api/v1/invoices/' || m.document_id
                      WHEN m.origin = 'PAGO' THEN '/api/v1/expenses/' || m.document_id
                 END AS document_url,
                 CASE WHEN m.original_event_id IS NOT NULL THEN 'REVERSAL'
                      WHEN EXISTS (
                        SELECT 1 FROM finance.cash_movements r
                        WHERE r.company_id = m.company_id AND r.original_event_id = m.event_id
                      ) THEN 'REVERSED' ELSE 'CONFIRMED' END AS status
          FROM finance.cash_movements m
          LEFT JOIN finance.manual_cash_operations op
            ON op.company_id = m.company_id AND op.id = m.manual_operation_id
          WHERE m.company_id = :company
            AND (op.id IS NULL OR m.event_type <> 'INTERNAL_TRANSFER' OR m.direction = 'OUTFLOW')
      ), filtered AS (
          SELECT * FROM movements
          WHERE (:type = '' OR type = :type) AND (:origin = '' OR origin = :origin)
            AND (:method = '' OR method = :method) AND (:category = '' OR category = :category)
            AND (:search = '' OR strpos(lower(concept), lower(:search)) > 0
                 OR strpos(lower(reference), lower(:search)) > 0
                 OR strpos(reference_id::text, lower(:search)) > 0)
      )
      """;
  private static final String PERIOD =
      """
      WHERE (CAST(:from AS date) IS NULL OR date >= :from)
        AND (CAST(:to AS date) IS NULL OR date <= :to)
      """;

  private final JdbcClient jdbc;

  PostgresCashMovementQueries(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Page page(UUID company, Criteria criteria, int page, int size) {
    long total =
        query("SELECT count(*) FROM filtered " + PERIOD, company, criteria)
            .query(Long.class)
            .single();
    return new Page(
        rows(company, criteria, size, (long) page * size),
        page,
        size,
        total,
        (total + size - 1) / size);
  }

  @Override
  public List<Row> all(UUID company, Criteria criteria) {
    return rows(company, criteria, ALL_LIMIT + 1, 0);
  }

  @Override
  public Summary summary(UUID company, Criteria criteria) {
    return query(
            """
            SELECT COALESCE(sum(amount) FILTER (WHERE in_period AND type = 'INCOME'), 0) AS inflows,
                   COALESCE(sum(amount) FILTER (WHERE in_period AND type = 'EXPENSE'), 0) AS outflows,
                   COALESCE(sum(CASE type WHEN 'INCOME' THEN amount WHEN 'EXPENSE' THEN -amount END)
                       FILTER (WHERE NOT in_period), 0) AS opening,
                   count(*) FILTER (WHERE in_period) AS movement_count
            FROM (
                SELECT type, amount, (CAST(:from AS date) IS NULL OR date >= :from) AS in_period
                FROM filtered
                WHERE currency = 'DOP' AND (CAST(:to AS date) IS NULL OR date <= :to)
            ) period
            """,
            company,
            criteria)
        .query((row, index) -> {
          var inflows = row.getBigDecimal("inflows");
          var outflows = row.getBigDecimal("outflows");
          var opening = row.getBigDecimal("opening");
          var net = inflows.subtract(outflows);
          return new Summary(
              inflows, outflows, net, opening, opening.add(net), row.getLong("movement_count"));
        })
        .single();
  }

  private List<Row> rows(UUID company, Criteria criteria, int limit, long offset) {
    return query(
            "SELECT * FROM filtered "
                + PERIOD
                + " ORDER BY occurred_at DESC, id ASC LIMIT :limit OFFSET :offset",
            company,
            criteria)
        .param("limit", limit)
        .param("offset", offset)
        .query(PostgresCashMovementQueries::row)
        .list();
  }

  private JdbcClient.StatementSpec query(String sql, UUID company, Criteria criteria) {
    return jdbc.sql(FILTERED + sql)
        .param("company", company)
        .param("search", criteria.search())
        .param("type", code(criteria.type()))
        .param("origin", code(criteria.origin()))
        .param("method", code(criteria.method()))
        .param("category", criteria.category() == null ? "" : criteria.category())
        .param("from", criteria.from(), java.sql.Types.DATE)
        .param("to", criteria.to(), java.sql.Types.DATE);
  }

  private static String code(Enum<?> filter) {
    return filter == null ? "" : filter.name();
  }

  private static Row row(ResultSet row, int index) throws SQLException {
    return new Row(
        row.getObject("id", UUID.class),
        row.getObject("reference_id", UUID.class),
        row.getString("reference"),
        row.getObject("date", LocalDate.class),
        row.getString("concept"),
        row.getString("category"),
        row.getString("method"),
        row.getBigDecimal("amount"),
        row.getString("currency"),
        row.getString("type"),
        row.getString("origin"),
        row.getObject("account_id", UUID.class),
        row.getObject("destination_account_id", UUID.class),
        row.getString("person_or_entity"),
        null,
        row.getObject("document_id", UUID.class),
        row.getString("document_url"),
        row.getString("observations"),
        row.getString("status"));
  }
}
