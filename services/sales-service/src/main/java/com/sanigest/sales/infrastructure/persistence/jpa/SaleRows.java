package com.sanigest.sales.infrastructure.persistence.jpa;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.Repository;

interface SaleRows extends Repository<SaleRow, UUID> {
  @EntityGraph(attributePaths = "lines")
  Optional<SaleRow> findByCompanyIdAndId(UUID companyId, UUID id);
}
