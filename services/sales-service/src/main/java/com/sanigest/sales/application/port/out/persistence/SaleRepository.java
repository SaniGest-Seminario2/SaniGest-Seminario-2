package com.sanigest.sales.application.port.out.persistence;

import com.sanigest.sales.domain.model.Sale;
import java.util.Optional;
import java.util.UUID;

public interface SaleRepository {
  void insert(Sale sale);

  /** Assigns a commercial code and inserts the aggregate in one local transaction. */
  Sale register(Sale sale);

  Optional<Sale> findByCompanyIdAndId(UUID companyId, UUID saleId);
}
