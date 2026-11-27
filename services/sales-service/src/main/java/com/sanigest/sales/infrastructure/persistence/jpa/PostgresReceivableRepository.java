package com.sanigest.sales.infrastructure.persistence.jpa;

import com.sanigest.sales.application.dto.ReceivableDetail;
import com.sanigest.sales.application.dto.ReceivablePage;
import com.sanigest.sales.application.dto.ReceivableSearch;
import com.sanigest.sales.application.port.out.persistence.ReceivableRepository;
import com.sanigest.sales.domain.enums.ReceivableOrigin;
import com.sanigest.sales.domain.enums.ReceivableStatus;
import com.sanigest.sales.domain.model.Receivable;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Repository
class PostgresReceivableRepository implements ReceivableRepository {
  private static final String SELECT_DETAIL =
      "SELECT r.*, s.code AS sale_code, i.id AS invoice_id, i.number AS invoice_number ";
  private static final String JOINS =
      """
      FROM sales.receivables r
      LEFT JOIN sales.sales s ON s.company_id = r.company_id AND s.id = r.sale_id
      LEFT JOIN sales.internal_invoices i ON i.company_id = r.company_id AND i.sale_id = r.sale_id
      """;
  private static final String OPEN_FILTER =
      """
      WHERE r.company_id = :company AND r.balance > 0 AND r.status IN ('OPEN', 'PARTIALLY_PAID')
        AND (:status = 'ALL' OR r.status = :status)
        AND (:search = ''
          OR strpos(lower(r.client_name), lower(:search)) > 0
          OR strpos(lower(s.code), lower(:search)) > 0
          OR strpos(lower(i.number), lower(:search)) > 0
          OR strpos(CAST(COALESCE(i.id, s.id) AS text), lower(:search)) > 0)
      """;
  private static final String OPEN_LIST =
      SELECT_DETAIL + JOINS + OPEN_FILTER + " ORDER BY r.due_date, r.id";

  private final JdbcClient jdbc;

  PostgresReceivableRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  void insert(Receivable receivable, LocalDate saleDate) {
    jdbc.sql(
            """
            INSERT INTO sales.receivables
              (id, company_id, sale_id, sale_date, client_id, client_name, total_amount, balance,
               due_date, status, created_at, origin)
            VALUES (:id, :company, :sale, :saleDate, :client, :name, :total, :balance,
                    :dueDate, :status, :createdAt, :origin)
            """)
        .param("id", receivable.id())
        .param("company", receivable.companyId())
        .param("sale", receivable.saleId())
        .param("saleDate", saleDate)
        .param("client", receivable.clientId())
        .param("name", receivable.clientName())
        .param("total", receivable.totalAmount())
        .param("balance", receivable.balance())
        .param("dueDate", receivable.dueDate())
        .param("status", receivable.status().name())
        .param("createdAt", java.sql.Timestamp.from(receivable.createdAt()))
        .param("origin", receivable.origin().name())
        .update();
  }

  @Override
  public Optional<ReceivableDetail> findByCompanyIdAndId(UUID companyId, UUID id) {
    return jdbc.sql(SELECT_DETAIL + JOINS + "WHERE r.company_id = :company AND r.id = :id")
        .param("company", companyId)
        .param("id", id)
        .query(PostgresReceivableRepository::toDetail)
        .optional();
  }

  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public ReceivablePage page(UUID companyId, ReceivableSearch criteria, int page, int size) {
    long total =
        openQuery("SELECT count(*) " + JOINS + OPEN_FILTER, companyId, criteria)
            .query(Long.class)
            .single();
    var content =
        openQuery(OPEN_LIST + " LIMIT :size OFFSET :offset", companyId, criteria)
            .param("size", size)
            .param("offset", (long) page * size)
            .query(PostgresReceivableRepository::toDetail)
            .list();
    return new ReceivablePage(content, page, size, total);
  }

  @Override
  public List<ReceivableDetail> all(UUID companyId, ReceivableSearch criteria) {
    return openQuery(OPEN_LIST, companyId, criteria)
        .query(PostgresReceivableRepository::toDetail)
        .list();
  }

  private JdbcClient.StatementSpec openQuery(
      String sql, UUID companyId, ReceivableSearch criteria) {
    var status = criteria.status().receivableStatus();
    return jdbc.sql(sql)
        .param("company", companyId)
        .param("status", status == null ? "ALL" : status.name())
        .param("search", criteria.search());
  }

  private static ReceivableDetail toDetail(ResultSet row, int index) throws SQLException {
    return new ReceivableDetail(
        toReceivable(row),
        row.getString("sale_code"),
        row.getObject("invoice_id", UUID.class),
        row.getString("invoice_number"));
  }

  Optional<Receivable> lockBySale(UUID companyId, UUID saleId) {
    return find("WHERE company_id = :company AND sale_id = :id FOR UPDATE", companyId, saleId);
  }

  void updateStatus(Receivable receivable) {
    int updated =
        jdbc.sql(
                """
                UPDATE sales.receivables SET status = :status WHERE company_id = :company AND id = :id
                """)
            .param("status", receivable.status().name())
            .param("company", receivable.companyId())
            .param("id", receivable.id())
            .update();
    if (updated != 1) {
      throw new IllegalStateException("Locked receivable could not be updated");
    }
  }

  void applyPayment(Receivable receivable, BigDecimal balance) {
    int updated =
        jdbc.sql(
                """
                UPDATE sales.receivables SET balance = :balance,
                    status = sales.payment_status(total_amount, :balance)
                 WHERE company_id = :company AND id = :id
                """)
            .param("balance", balance)
            .param("company", receivable.companyId())
            .param("id", receivable.id())
            .update();
    if (updated != 1) {
      throw new IllegalStateException("Locked receivable could not be updated");
    }
  }

  private Optional<Receivable> find(String where, UUID companyId, UUID id) {
    return jdbc.sql(
            """
            SELECT id, company_id, sale_id, client_id, client_name, total_amount, balance,
                   due_date, status, created_at, origin FROM sales.receivables
            """
                + where)
        .param("company", companyId)
        .param("id", id)
        .query((row, index) -> toReceivable(row))
        .optional();
  }

  private static Receivable toReceivable(ResultSet row) throws SQLException {
    return new Receivable(
        row.getObject("id", UUID.class),
        row.getObject("company_id", UUID.class),
        row.getObject("sale_id", UUID.class),
        row.getObject("client_id", UUID.class),
        row.getString("client_name"),
        row.getBigDecimal("total_amount"),
        row.getBigDecimal("balance"),
        row.getObject("due_date", LocalDate.class),
        ReceivableStatus.valueOf(row.getString("status")),
        row.getTimestamp("created_at").toInstant(),
        ReceivableOrigin.valueOf(row.getString("origin")));
  }
}
