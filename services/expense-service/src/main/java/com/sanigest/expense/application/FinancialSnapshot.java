package com.sanigest.expense.application;

import java.util.UUID;

public interface FinancialSnapshot {
  String read(UUID company);
}
