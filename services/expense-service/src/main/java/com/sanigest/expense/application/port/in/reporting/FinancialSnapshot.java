package com.sanigest.expense.application.port.in.reporting;

import java.util.UUID;

public interface FinancialSnapshot {
  String read(UUID company);
}
