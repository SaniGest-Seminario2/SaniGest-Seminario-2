package com.sanigest.expense.application.port.out.persistence;

import com.sanigest.expense.application.dto.ExpenseRegistration.Document;
import com.sanigest.expense.application.dto.ExpenseRegistration.RegistrationKey;
import java.util.Optional;
import java.util.UUID;

public interface ExpenseStore {
  record Stored(String requestHash, Document document, boolean inventorySynced) {}

  Optional<Stored> find(RegistrationKey key);

  Stored save(RegistrationKey key, String requestHash, Document document);

  void markInventorySynced(RegistrationKey key, UUID expenseId);
}
