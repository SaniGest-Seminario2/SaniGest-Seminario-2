package com.sanigest.catalog.domain;

import java.util.Objects;
import java.util.UUID;

public record Client(UUID id, UUID companyId, ClientDetails details, Status status) {
  public enum Status {
    ACTIVE,
    INACTIVE
  }

  public Client {
    Objects.requireNonNull(id);
    Objects.requireNonNull(companyId);
    Objects.requireNonNull(details);
    Objects.requireNonNull(status);
  }

  public Client updateDetails(ClientPatch patch) {
    return new Client(id, companyId, patch.applyTo(details), status);
  }

  public Client changeStatus(Status target) {
    if (target == null) {
      throw new InvalidClientPatch();
    }
    return target == status ? this : new Client(id, companyId, details, target);
  }

  public static Client register(UUID companyId, ClientDetails details) {
    return new Client(
        UUID.randomUUID(),
        Objects.requireNonNull(companyId),
        Objects.requireNonNull(details),
        Status.ACTIVE);
  }
}
