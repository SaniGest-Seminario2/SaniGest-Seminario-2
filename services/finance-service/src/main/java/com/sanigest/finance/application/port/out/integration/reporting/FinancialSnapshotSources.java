package com.sanigest.finance.application.port.out.integration.reporting;

import com.sanigest.finance.application.dto.reporting.FinancialSnapshots;
import java.util.UUID;

public interface FinancialSnapshotSources {
  void authorize(UUID company, String token);

  FinancialSnapshots fetch(UUID company, String token);
}
