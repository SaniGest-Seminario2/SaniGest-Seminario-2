package com.sanigest.sales.infrastructure.persistence.jpa;

import com.sanigest.sales.application.port.out.persistence.ReceivableRepository;
import com.sanigest.sales.domain.enums.ReceivableOrigin;
import com.sanigest.sales.domain.enums.ReceivableStatus;
import com.sanigest.sales.domain.model.Receivable;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class PostgresReceivableRepository implements ReceivableRepository {
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
  public Optional<Receivable> findByCompanyIdAndId(UUID companyId, UUID id) {
    return find("WHERE company_id = :company AND id = :id", companyId, id);
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

  private Optional<Receivable> find(String where, UUID companyId, UUID id) {
    return jdbc.sql(
            """
            SELECT id, company_id, sale_id, client_id, client_name, total_amount, balance,
                   due_date, status, created_at, origin FROM sales.receivables
            """
                + where)
        .param("company", companyId)
        .param("id", id)
        .query((row, index) ->
            new Receivable(
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
                ReceivableOrigin.valueOf(row.getString("origin"))))
        .optional();
  }
}
