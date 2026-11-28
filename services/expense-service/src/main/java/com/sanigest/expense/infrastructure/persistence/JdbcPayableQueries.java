package com.sanigest.expense.infrastructure.persistence;

import com.sanigest.expense.application.port.in.PayableQueries;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JdbcPayableQueries implements PayableQueries {
  private static final String SOURCE =
      """
      FROM expense.payable_balances p
      JOIN expense.documents d ON d.company_id = p.company_id AND d.id = p.expense_id
      CROSS JOIN LATERAL (
          SELECT string_agg(item_name, ', ' ORDER BY position) AS concept
          FROM jsonb_array_elements(NULLIF(d.snapshot -> 'lines', 'null'::jsonb))
          WITH ORDINALITY AS line(item, position)
          CROSS JOIN LATERAL (SELECT NULLIF(btrim(item ->> 'itemName'), '') AS item_name) name
      ) c
      WHERE p.company_id = :company AND p.outstanding_amount > 0
      """;
  private static final String SELECT =
      """
      SELECT p.expense_id, p.supplier_id, p.supplier_name, c.concept, p.due_date,
             p.original_amount, p.paid_amount, p.outstanding_amount, p.status
      """;
  private static final String ORDER = " ORDER BY p.due_date, p.expense_id";
  private static final RowMapper<Payable> PAYABLE =
      (row, index) ->
          new Payable(
              row.getObject("expense_id", UUID.class),
              row.getObject("supplier_id", UUID.class),
              row.getString("supplier_name"),
              row.getString("concept"),
              row.getObject("due_date", LocalDate.class),
              row.getBigDecimal("original_amount"),
              row.getBigDecimal("paid_amount"),
              row.getBigDecimal("outstanding_amount"),
              Status.valueOf(row.getString("status")));

  private final JdbcClient jdbc;

  JdbcPayableQueries(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Page page(UUID company, Criteria criteria, int page, int size) {
    long total = query("SELECT count(*) ", company, criteria, "").query(Long.class).single();
    var content =
        query(SELECT, company, criteria, ORDER + " LIMIT :limit OFFSET :offset")
            .param("limit", size)
            .param("offset", (long) page * size)
            .query(PAYABLE)
            .list();
    return new Page(content, page, size, total, Math.ceilDiv(total, size));
  }

  @Override
  public List<Payable> all(UUID company, Criteria criteria) {
    return query(SELECT, company, criteria, ORDER).query(PAYABLE).list();
  }

  @Override
  public List<SupplierBalance> supplierBalances(UUID company, List<UUID> supplierIds) {
    return jdbc.sql(
            """
            SELECT requested.supplier_id, COALESCE(balance.outstanding_amount, 0.00) AS outstanding
            FROM (SELECT DISTINCT unnest(CAST(:supplierIds AS uuid[])) AS supplier_id) requested
            LEFT JOIN expense.supplier_payable_balances balance
              ON balance.company_id = :company AND balance.supplier_id = requested.supplier_id
            ORDER BY requested.supplier_id
            """)
        .param("company", company)
        .param(
            "supplierIds",
            "{" + String.join(",", supplierIds.stream().map(UUID::toString).toList()) + "}")
        .query((row, index) ->
            new SupplierBalance(
                row.getObject("supplier_id", UUID.class), row.getBigDecimal("outstanding")))
        .list();
  }

  private JdbcClient.StatementSpec query(
      String select, UUID company, Criteria criteria, String suffix) {
    var sql = new StringBuilder(select).append(SOURCE);
    var search = criteria.search().strip();
    if (!search.isEmpty()) {
      sql.append(
          """
          AND (strpos(lower(p.supplier_name), lower(:search)) > 0
            OR strpos(lower(c.concept), lower(:search)) > 0
            OR strpos(lower(d.snapshot ->> 'supplierInvoiceNumber'), lower(:search)) > 0)
          """);
    }
    if (criteria.status() != Filter.ALL) {
      sql.append(" AND p.status = :status");
    }
    if (criteria.supplierId() != null) {
      sql.append(" AND p.supplier_id = :supplierId");
    }
    var statement = jdbc.sql(sql.append(suffix).toString()).param("company", company);
    if (!search.isEmpty()) {
      statement.param("search", search);
    }
    if (criteria.status() != Filter.ALL) {
      statement.param(
          "status",
          criteria.status() == Filter.PENDING ? Status.OPEN.name() : Status.PARTIALLY_PAID.name());
    }
    if (criteria.supplierId() != null) {
      statement.param("supplierId", criteria.supplierId());
    }
    return statement;
  }
}
