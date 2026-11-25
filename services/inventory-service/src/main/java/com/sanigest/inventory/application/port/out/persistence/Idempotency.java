package com.sanigest.inventory.application.port.out.persistence;

import java.util.Optional;
import java.util.UUID;

public interface Idempotency {
  enum Operation {
    INITIALIZE_STOCK,
    ADJUST_STOCK
  }

  record Key(UUID company, UUID actor, Operation operation, String value) {}

  <T> Optional<T> replay(Key key, String fingerprint, Class<T> type);

  boolean claim(Key key, String fingerprint);

  void complete(Key key, Object result);
}
