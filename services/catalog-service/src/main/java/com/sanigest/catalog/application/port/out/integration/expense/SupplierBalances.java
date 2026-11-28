package com.sanigest.catalog.application.port.out.integration.expense;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface SupplierBalances {
  Map<UUID, BigDecimal> read(UUID companyId, List<UUID> supplierIds);

  class Unavailable extends RuntimeException {}
}
