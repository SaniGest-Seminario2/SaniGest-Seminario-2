package com.sanigest.finance.application.port.out.persistence.movement;

import com.sanigest.finance.application.dto.movement.ManualMovement.Account;
import com.sanigest.finance.application.dto.movement.ManualMovement.AccountBalance;
import com.sanigest.finance.application.dto.movement.ManualMovement.Document;
import com.sanigest.finance.application.dto.movement.ManualMovement.Request;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ManualMovementStore {
  List<Account> accounts(UUID company);

  List<AccountBalance> balances(UUID company);

  UUID save(
      UUID company, UUID actor, String key, String fingerprint, Request request, Document document);

  Optional<Document> document(UUID company, UUID id);
}
