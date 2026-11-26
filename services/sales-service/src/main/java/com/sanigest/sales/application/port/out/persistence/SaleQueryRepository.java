package com.sanigest.sales.application.port.out.persistence;

import com.sanigest.sales.application.dto.SaleDetail;
import com.sanigest.sales.application.dto.SalePage;
import com.sanigest.sales.application.dto.SaleSearch;
import java.util.Optional;
import java.util.UUID;

public interface SaleQueryRepository {
  Optional<SaleDetail> detail(UUID companyId, UUID saleId);

  SalePage search(UUID companyId, SaleSearch criteria);
}
