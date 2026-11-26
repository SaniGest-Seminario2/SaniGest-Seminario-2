package com.sanigest.sales.application.port.out.persistence;

import com.sanigest.sales.application.dto.ReceivableDetail;
import java.util.Optional;
import java.util.UUID;

public interface ReceivableRepository {
  Optional<ReceivableDetail> findByCompanyIdAndId(UUID companyId, UUID id);
}
