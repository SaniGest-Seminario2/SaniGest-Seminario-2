package com.sanigest.sales.infrastructure.persistence.jpa;

import com.sanigest.sales.application.port.out.persistence.SaleRepository;
import com.sanigest.sales.domain.model.Sale;
import jakarta.persistence.EntityManager;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
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
  @Transactional(readOnly = true)
  public Optional<Sale> findByCompanyIdAndId(UUID companyId, UUID saleId) {
    return rows.findByCompanyIdAndId(
            Objects.requireNonNull(companyId, "companyId"),
            Objects.requireNonNull(saleId, "saleId"))
        .map(SaleRow::toDomain);
  }
}
