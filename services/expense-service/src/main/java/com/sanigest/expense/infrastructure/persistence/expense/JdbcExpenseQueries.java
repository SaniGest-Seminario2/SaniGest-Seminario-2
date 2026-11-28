package com.sanigest.expense.infrastructure.persistence.expense;

import com.sanigest.expense.application.dto.expense.ExpenseRegistration.Document;
import com.sanigest.expense.application.dto.expense.ExpenseRegistration.OperationType;
import com.sanigest.expense.application.port.in.expense.ExpenseQueries;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Repository
class JdbcExpenseQueries implements ExpenseQueries {
  private static final String SOURCE =
      """
      FROM expense.document_balances d
      CROSS JOIN LATERAL (
          SELECT string_agg(NULLIF(btrim(item ->> 'itemName'), ''), ', ' ORDER BY position) AS concept,
                 bool_or(strpos(lower(item ->> 'itemName'), lower(:search)) > 0) AS matches
          FROM jsonb_array_elements(d.snapshot -> 'lines') WITH ORDINALITY AS line(item, position)
      ) c
      WHERE d.company_id = :company
        AND d.cancelled_at IS NULL
        AND (:operationType = 'ALL' OR d.operation_type = :operationType)
        AND (:status = 'ALL' OR d.status = :status)
        AND (CAST(:from AS date) IS NULL OR d.document_date >= CAST(:from AS date))
        AND (CAST(:to AS date) IS NULL OR d.document_date <= CAST(:to AS date))
        AND (:search = '' OR c.matches
          OR strpos(lower(d.supplier_name), lower(:search)) > 0
          OR strpos(lower(d.supplier_invoice_number), lower(:search)) > 0
          OR strpos(lower(d.snapshot ->> 'ncf'), lower(:search)) > 0
          OR strpos(CAST(d.id AS text), lower(:search)) > 0)
      """;
  private static final String SELECT =
      """
      SELECT d.id, d.supplier_invoice_number, c.concept, d.supplier_id, d.supplier_name,
             d.document_date, d.operation_type, d.total, d.outstanding_amount, d.status
      """;
  private static final String ORDER = " ORDER BY d.document_date DESC, d.id";
  private static final RowMapper<Row> ROW =
      (row, index) ->
          new Row(
              row.getObject("id", UUID.class), row.getString("supplier_invoice_number"),
              row.getString("concept"), row.getObject("supplier_id", UUID.class),
              row.getString("supplier_name"), row.getObject("document_date", LocalDate.class),
              OperationType.valueOf(row.getString("operation_type")), row.getBigDecimal("total"),
              row.getBigDecimal("outstanding_amount"), Status.valueOf(row.getString("status")));

  private final JdbcClient jdbc;
  private final ObjectMapper json;

  JdbcExpenseQueries(JdbcClient jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Page page(UUID company, Criteria criteria, int page, int size) {
    long total = query("SELECT count(*) ", company, criteria, "").query(Long.class).single();
    var content =
        query(SELECT, company, criteria, ORDER + " LIMIT :limit OFFSET :offset")
            .param("limit", size)
            .param("offset", (long) page * size)
            .query(ROW)
            .list();
    return new Page(content, page, size, total, Math.ceilDiv(total, size));
  }

  @Override
  public List<Row> all(UUID company, Criteria criteria) {
    return query(SELECT, company, criteria, ORDER).query(ROW).list();
  }

  @Override
  public Optional<Detail> detail(UUID company, UUID id) {
    return jdbc.sql(
            """
            SELECT snapshot, paid_amount, outstanding_amount, status, cancelled_at, cancellation_reason
            FROM expense.document_balances
            WHERE company_id = :company AND id = :id
            """)
        .param("company", company)
        .param("id", id)
        .query((row, index) -> {
          var document = json.readValue(row.getString("snapshot"), Document.class);
          var cancelledAt = row.getObject("cancelled_at", OffsetDateTime.class);
          return new Detail(
              document.id(),
              document.supplierInvoiceNumber(),
              document.supplierId(),
              document.supplierName(),
              document.documentDate(),
              document.operationType(),
              document.total(),
              row.getBigDecimal("paid_amount"),
              row.getBigDecimal("outstanding_amount"),
              Status.valueOf(row.getString("status")),
              document.lines(),
              document.attachmentId() == null ? List.of() : List.of(document.attachmentId()),
              cancelledAt == null ? null : cancelledAt.toInstant(),
              row.getString("cancellation_reason"));
        })
        .optional();
  }

  @Override
  public Summary summary(UUID company, Criteria criteria) {
    return query(
            """
            SELECT COALESCE(sum(d.total) FILTER (WHERE d.operation_type = 'PURCHASE'), 0.00),
                   COALESCE(sum(d.total) FILTER (WHERE d.operation_type = 'OPERATING_EXPENSE'), 0.00),
                   COALESCE(sum(d.total) FILTER (WHERE d.operation_type = 'ASSET_ACQUISITION'), 0.00),
                   COALESCE(sum(d.outstanding_amount), 0.00),
                   COALESCE(sum(d.paid_amount), 0.00),
                   count(*)
            """,
            company,
            criteria,
            "")
        .query((row, index) ->
            new Summary(
                row.getBigDecimal(1),
                row.getBigDecimal(2),
                row.getBigDecimal(3),
                row.getBigDecimal(4),
                row.getBigDecimal(5),
                row.getLong(6)))
        .single();
  }

  private JdbcClient.StatementSpec query(
      String select, UUID company, Criteria criteria, String suffix) {
    return jdbc.sql(select + SOURCE + suffix)
        .param("company", company)
        .param("search", criteria.search())
        .param(
            "operationType",
            criteria.operationType() == null ? "ALL" : criteria.operationType().name())
        .param("status", criteria.status() == null ? "ALL" : criteria.status().name())
        .param("from", criteria.from(), java.sql.Types.DATE)
        .param("to", criteria.to(), java.sql.Types.DATE);
  }
}
