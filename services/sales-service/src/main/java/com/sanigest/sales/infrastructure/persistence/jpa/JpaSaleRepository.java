package com.sanigest.sales.infrastructure.persistence.jpa;

import com.sanigest.sales.application.exception.SaleCancellationConflict;
import com.sanigest.sales.application.port.out.persistence.SaleRepository;
import com.sanigest.sales.domain.model.Sale;
import com.sanigest.sales.domain.model.SaleCancellation;
import jakarta.persistence.EntityManager;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JpaSaleRepository implements SaleRepository {
  private final EntityManager entityManager;
  private final SaleRows rows;

  JpaSaleRepository(EntityManager entityManager, SaleRows rows) {
    this.entityManager = entityManager;
    this.rows = rows;
  }

  @Override
  @Transactional
  public void insert(Sale sale) {
    entityManager.persist(new SaleRow(sale));
  }

  @Override
  @Transactional
  public Sale register(Sale sale) {
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
          if (rows.recordCancellation(
                  companyId,
                  saleId,
                  cancellation.cancelledBy(),
                  cancellation.cancelledAt(),
                  cancellation.cancelReason())
              != 1) {
            throw new SaleCancellationConflict();
          }
          return cancelled;
        });
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
