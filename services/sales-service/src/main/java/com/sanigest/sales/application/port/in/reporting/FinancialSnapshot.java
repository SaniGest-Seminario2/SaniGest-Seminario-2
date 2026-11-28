package com.sanigest.sales.application.port.in.reporting;

import java.util.UUID;

public interface FinancialSnapshot {
  String read(UUID company);
}
