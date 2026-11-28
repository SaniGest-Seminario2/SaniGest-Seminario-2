package com.sanigest.sales.infrastructure.persistence.jpa.client;

import com.sanigest.sales.application.usecase.client.ClientFinancials.CompanyTotals;
import com.sanigest.sales.application.usecase.client.ClientFinancials.Invoice;
import com.sanigest.sales.application.usecase.client.ClientFinancials.Ledger;
import com.sanigest.sales.application.usecase.client.ClientFinancials.Payment;
import com.sanigest.sales.application.usecase.client.ClientFinancials.Statement;
import com.sanigest.sales.application.usecase.client.ClientFinancials.Totals;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Repository
class PostgresClientFinancials implements Ledger {
  private static final String OPEN_RECEIVABLES =
      """
      FROM sales.receivables r
      WHERE r.company_id = :company AND r.status IN ('OPEN', 'PARTIALLY_PAID')
      """;
  private static final String CLIENT_INVOICES =
      """
      FROM sales.internal_invoices i
      WHERE i.company_id = :company AND i.client_id = :client
      """;
  private static final String CLIENT_PAYMENTS =
      """
      FROM sales.invoice_payments p
      JOIN sales.internal_invoices i ON i.company_id = p.company_id AND i.id = p.invoice_id
      WHERE i.company_id = :company AND i.client_id = :client AND p.registered_at IS NOT NULL
      """;

  private final JdbcClient jdbc;

  PostgresClientFinancials(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Map<UUID, BigDecimal> balances(UUID company, List<UUID> clientIds) {
    return jdbc
        .sql(
            "SELECT r.client_id, sum(r.balance) "
                + OPEN_RECEIVABLES
                + " AND r.client_id IN (:clients) GROUP BY r.client_id")
        .param("company", company)
        .param("clients", clientIds)
        .query((rs, row) -> Map.entry(rs.getObject(1, UUID.class), rs.getBigDecimal(2)))
        .list()
        .stream()
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  @Override
  public CompanyTotals companyTotals(UUID company) {
    return jdbc.sql(
            """
            SELECT count(DISTINCT r.client_id), coalesce(sum(r.balance), 0), statement_timestamp()
            """
                + OPEN_RECEIVABLES)
        .param("company", company)
        .query((rs, row) ->
            new CompanyTotals(rs.getLong(1), rs.getBigDecimal(2), rs.getTimestamp(3).toInstant()))
        .single();
  }

  @Override
  public Totals totals(UUID company, UUID clientId) {
    return clientQuery(
            """
            SELECT coalesce(sum(r.balance), 0),
                   coalesce(sum(r.balance) FILTER (WHERE r.origin = 'OPENING_BALANCE'
                     OR EXISTS (SELECT 1 FROM sales.sales s
                       WHERE s.company_id = r.company_id AND s.id = r.sale_id
                         AND s.mode = 'CREDIT')), 0),
                   (SELECT coalesce(sum(i.total), 0)
            """
                + CLIENT_INVOICES
                + " AND i.status = 'ISSUED'), "
                + "(SELECT coalesce(sum(p.amount), 0) "
                + CLIENT_PAYMENTS
                + "), statement_timestamp() "
                + OPEN_RECEIVABLES
                + " AND r.client_id = :client",
            company,
            clientId)
        .query((rs, row) ->
            new Totals(
                rs.getBigDecimal(1),
                rs.getBigDecimal(2),
                rs.getBigDecimal(3),
                rs.getBigDecimal(4),
                rs.getTimestamp(5).toInstant()))
        .single();
  }

  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Statement statement(UUID company, UUID clientId, int page, int size) {
    Instant asOf =
        jdbc.sql("SELECT transaction_timestamp()")
            .query(java.sql.Timestamp.class)
            .single()
            .toInstant();
    long offset = (long) page * size;
    var invoices =
        clientQuery(
                "SELECT i.id, i.sale_date, i.total, i.status "
                    + CLIENT_INVOICES
                    + " ORDER BY i.sale_date DESC, i.id DESC LIMIT :limit OFFSET :offset",
                company,
                clientId)
            .param("limit", size + 1)
            .param("offset", offset)
            .query((rs, row) ->
                new Invoice(
                    rs.getObject(1, UUID.class),
                    rs.getObject(2, LocalDate.class),
                    rs.getBigDecimal(3),
                    rs.getString(4)))
            .list();
    var payments =
        clientQuery(
                "SELECT p.id, p.invoice_id, p.registered_at, p.amount "
                    + CLIENT_PAYMENTS
                    + " ORDER BY p.registered_at DESC, p.id DESC LIMIT :limit OFFSET :offset",
                company,
                clientId)
            .param("limit", size + 1)
            .param("offset", offset)
            .query((rs, row) ->
                new Payment(
                    rs.getObject(1, UUID.class),
                    rs.getObject(2, UUID.class),
                    rs.getTimestamp(3).toInstant(),
                    rs.getBigDecimal(4)))
            .list();
    return new Statement(
        invoices.stream().limit(size).toList(),
        payments.stream().limit(size).toList(),
        page,
        size,
        invoices.size() > size,
        payments.size() > size,
        asOf);
  }

  private JdbcClient.StatementSpec clientQuery(String sql, UUID company, UUID clientId) {
    return jdbc.sql(sql).param("company", company).param("client", clientId);
  }
}
