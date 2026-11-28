package com.sanigest.expense.application.port.in;

import java.util.UUID;

public interface FinancialSnapshot {
  String read(UUID company);
}
