package com.sanigest.sales.application.port.out.persistence;

import com.sanigest.sales.application.dto.SalePage;
import com.sanigest.sales.application.dto.SaleSearch;
import java.util.UUID;

public interface SaleQueryRepository {
  SalePage search(UUID companyId, SaleSearch criteria);
}
