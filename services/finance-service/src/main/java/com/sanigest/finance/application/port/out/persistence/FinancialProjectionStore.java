package com.sanigest.finance.application.port.out.persistence;

import com.sanigest.finance.application.dto.FinancialSnapshots;
import java.util.UUID;

public interface FinancialProjectionStore {
  void save(UUID company, FinancialSnapshots snapshots);

  String view(UUID company);
}
