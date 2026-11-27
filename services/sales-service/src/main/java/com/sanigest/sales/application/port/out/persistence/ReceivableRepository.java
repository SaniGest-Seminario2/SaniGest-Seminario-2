package com.sanigest.sales.application.port.out.persistence;

import com.sanigest.sales.application.dto.ReceivableDetail;
import com.sanigest.sales.application.dto.ReceivablePage;
import com.sanigest.sales.application.dto.ReceivableSearch;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReceivableRepository {
  ReceivablePage page(UUID companyId, ReceivableSearch criteria, int page, int size);

  List<ReceivableDetail> all(UUID companyId, ReceivableSearch criteria);

  Optional<ReceivableDetail> findByCompanyIdAndId(UUID companyId, UUID id);
}
