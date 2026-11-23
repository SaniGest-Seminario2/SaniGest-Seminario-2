package com.sanigest.sales.application.port.out.persistence;

import com.sanigest.sales.domain.model.Receivable;
import java.util.Optional;
import java.util.UUID;

public interface ReceivableRepository {
  Optional<Receivable> findByCompanyIdAndId(UUID companyId, UUID id);
}
