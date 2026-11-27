package com.sanigest.sales.application;

import java.util.UUID;

public interface FinancialSnapshot {
  String read(UUID company);
}
