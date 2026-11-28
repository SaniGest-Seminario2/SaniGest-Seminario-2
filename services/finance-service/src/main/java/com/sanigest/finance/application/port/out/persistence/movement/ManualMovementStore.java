package com.sanigest.finance.application.port.out.persistence.movement;

import com.sanigest.finance.application.dto.movement.ManualMovement.Document;
import com.sanigest.finance.application.dto.movement.ManualMovement.Request;
import java.util.Optional;
import java.util.UUID;

public interface ManualMovementStore {
  UUID save(
      UUID company, UUID actor, String key, String fingerprint, Request request, Document document);

  Optional<Document> document(UUID company, UUID id);
}
