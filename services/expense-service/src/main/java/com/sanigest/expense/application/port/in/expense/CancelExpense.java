package com.sanigest.expense.application.port.in.expense;

import java.util.UUID;

public interface CancelExpense {
  void cancel(UUID company, UUID actor, UUID id, String reason);
}
