package com.sanigest.catalog.domain;

import java.util.Objects;
import java.util.UUID;

public record Client(UUID id, UUID companyId, ClientDetails details, Status status) {
  public enum Status {
    ACTIVE
  }

  public static Client register(UUID companyId, ClientDetails details) {
    return new Client(
        UUID.randomUUID(),
        Objects.requireNonNull(companyId),
        Objects.requireNonNull(details),
        Status.ACTIVE);
  }
}
