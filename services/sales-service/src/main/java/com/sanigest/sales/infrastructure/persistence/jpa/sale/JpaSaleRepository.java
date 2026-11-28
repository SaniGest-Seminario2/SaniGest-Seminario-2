package com.sanigest.sales.infrastructure.persistence.jpa.sale;

import com.sanigest.sales.application.exception.sale.SaleCancellationConflict;
import com.sanigest.sales.application.port.out.persistence.sale.SaleRepository;
import com.sanigest.sales.domain.enums.receivable.ReceivableStatus;
import com.sanigest.sales.domain.enums.sale.SaleMode;
import com.sanigest.sales.domain.model.receivable.Receivable;
import com.sanigest.sales.domain.model.sale.Sale;
import com.sanigest.sales.domain.model.sale.SaleCancellation;
import com.sanigest.sales.infrastructure.persistence.audit.SalesAudit;
import com.sanigest.sales.infrastructure.persistence.jpa.receivable.PostgresReceivableRepository;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JpaSaleRepository implements SaleRepository {
  private final EntityManager entityManager;
  private final SaleRows rows;
  private final SalesAudit audit;
  private final JdbcClient jdbc;

  private final PostgresReceivableRepository receivables;

  JpaSaleRepository(
      EntityManager entityManager,
      SaleRows rows,
      PostgresReceivableRepository receivables,
      SalesAudit audit,
      JdbcClient jdbc) {
    this.jdbc = jdbc;
    this.receivables = receivables;
    this.entityManager = entityManager;
    this.rows = rows;
    this.audit = audit;
  }

  @Override
  @Transactional
  public void insert(Sale sale) {
    entityManager.persist(new SaleRow(sale));
  }

  @Override
  @Transactional
  public Sale register(Sale sale) {
    if (sale.mode() != SaleMode.CASH) {
      throw new IllegalArgumentException("Credit registration requires a receivable");
    }
    return registerSale(sale);
  }

  @Override
  @Transactional
  public Sale register(Sale sale, Receivable receivable) {
    if (!Receivable.fromSale(sale, receivable.dueDate(), receivable.createdAt())
        .equals(receivable)) {
      throw new IllegalArgumentException("Receivable does not match the sale");
    }
    Sale registered = registerSale(sale);
    entityManager.flush();
    receivables.insert(receivable, sale.saleDate());
    return registered;
  }

  private Sale registerSale(Sale sale) {
    int year = sale.saleDate().getYear();
    long sequence =
        ((Number)
                entityManager
                    .createNativeQuery(
                        """
                        WITH allocated AS (
                        INSERT INTO sales.sale_number_sequences AS counter (company_id, sale_year, last_number)
                        VALUES (:companyId, :year, 1)
                        ON CONFLICT (company_id, sale_year)
                        DO UPDATE SET last_number = counter.last_number + 1
                        RETURNING last_number
                        ) SELECT last_number FROM allocated
                        """)
                    .setParameter("companyId", sale.companyId())
                    .setParameter("year", year)
                    .getSingleResult())
            .longValue();
    Sale registered = sale.withCode(String.format(Locale.ROOT, "V-%04d-%03d", year, sequence));
    entityManager.persist(new SaleRow(registered));
    return registered;
  }

  @Override
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Optional<Sale> cancel(UUID companyId, UUID saleId, SaleCancellation cancellation) {
    return rows.lockSale(companyId, saleId)
        .flatMap(id -> findByCompanyIdAndId(companyId, id))
        .map(sale -> {
          Sale cancelled = sale.cancel(cancellation);
          if (cancelled == sale) {
            return sale;
          }
          receivables
              .lockBySale(companyId, saleId)
              .ifPresent(receivable -> {
                if (receivable.status() != ReceivableStatus.OPEN) {
                  throw new SaleCancellationConflict();
                }
                receivables.updateStatus(receivable.cancel());
              });
          if (rows.recordCancellation(
                  companyId,
                  saleId,
                  cancellation.cancelledBy(),
                  cancellation.cancelledAt(),
                  cancellation.cancelReason())
              != 1) {
            throw new SaleCancellationConflict();
          }
          cancelInvoice(companyId, saleId, cancellation);
          audit.append(
              companyId,
              cancellation.cancelledBy(),
              SalesAudit.Operation.SALE_CANCELLED,
              saleId,
              cancelled.status().name());
          return cancelled;
        });
  }

  private void cancelInvoice(UUID companyId, UUID saleId, SaleCancellation cancellation) {
    jdbc.sql(
            """
            UPDATE sales.internal_invoices
               SET status = 'CANCELLED', cancelled_by = :actor, cancelled_at = :at,
                   cancel_reason = :reason
             WHERE company_id = :company AND sale_id = :sale AND status = 'ISSUED'
            RETURNING id
            """)
        .param("actor", cancellation.cancelledBy())
        .param("at", Timestamp.from(cancellation.cancelledAt()))
        .param("reason", cancellation.cancelReason())
        .param("company", companyId)
        .param("sale", saleId)
        .query(UUID.class)
        .optional()
        .ifPresent(invoice ->
            audit.append(
                companyId,
                cancellation.cancelledBy(),
                SalesAudit.Operation.INVOICE_CANCELLED,
                invoice,
                "CANCELLED"));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Sale> findByCompanyIdAndId(UUID companyId, UUID saleId) {
    return rows.findByCompanyIdAndId(
            Objects.requireNonNull(companyId, "companyId"),
            Objects.requireNonNull(saleId, "saleId"))
        .map(SaleRow::toDomain);
  }
}
