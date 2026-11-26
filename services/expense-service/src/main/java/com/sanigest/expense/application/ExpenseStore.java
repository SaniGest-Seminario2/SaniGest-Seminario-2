package com.sanigest.expense.application;

import com.sanigest.expense.application.ExpenseRegistration.Document;
import java.util.Optional;
import java.util.UUID;

public interface ExpenseStore {
  record Key(UUID companyId, UUID actorId, String requestKey) {}

  record Stored(String requestHash, Document document, boolean inventorySynced) {}

  Optional<Stored> find(Key key);

  Stored save(Key key, String requestHash, Document document);

  void markInventorySynced(Key key, UUID expenseId);
}
