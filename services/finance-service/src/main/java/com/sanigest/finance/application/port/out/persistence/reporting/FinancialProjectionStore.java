package com.sanigest.finance.application.port.out.persistence.reporting;

import com.sanigest.finance.application.dto.reporting.FinancialSnapshots;
import java.util.UUID;

public interface FinancialProjectionStore {
  void save(UUID company, FinancialSnapshots snapshots);

  String view(UUID company);
}
