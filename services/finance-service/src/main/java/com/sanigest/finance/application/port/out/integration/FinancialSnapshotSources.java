package com.sanigest.finance.application.port.out.integration;

import com.sanigest.finance.application.dto.FinancialSnapshots;
import java.util.UUID;

public interface FinancialSnapshotSources {
  void authorize(UUID company, String token);

  FinancialSnapshots fetch(UUID company, String token);
}
