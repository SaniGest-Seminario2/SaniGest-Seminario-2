package com.sanigest.sales.application.port.out.persistence.sale;

import com.sanigest.sales.application.dto.sale.SaleDetail;
import com.sanigest.sales.application.dto.sale.SalePage;
import com.sanigest.sales.application.dto.sale.SaleSearch;
import com.sanigest.sales.application.dto.sale.SaleTotals;
import java.util.Optional;
import java.util.UUID;

public interface SaleQueryRepository {
  Optional<SaleDetail> detail(UUID companyId, UUID saleId);

  SalePage search(UUID companyId, SaleSearch criteria);

  SaleTotals totals(UUID companyId, SaleSearch criteria);
}
