package com.sanigest.sales.application.port.out.persistence.receivable;

import com.sanigest.sales.application.dto.receivable.ReceivableDetail;
import com.sanigest.sales.application.dto.receivable.ReceivablePage;
import com.sanigest.sales.application.dto.receivable.ReceivableSearch;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReceivableRepository {
  ReceivablePage page(UUID companyId, ReceivableSearch criteria, int page, int size);

  List<ReceivableDetail> all(UUID companyId, ReceivableSearch criteria);

  Optional<ReceivableDetail> findByCompanyIdAndId(UUID companyId, UUID id);
}
