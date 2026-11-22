package com.sanigest.catalog.api.dto;

import com.sanigest.catalog.domain.enums.ClientStatus;
import com.sanigest.catalog.domain.model.Client;
import java.util.UUID;

public record ClientResponse(UUID id, String name, String taxId, ClientStatus status) {
  public static ClientResponse from(Client client) {
    return new ClientResponse(
        client.id(), client.details().name(), client.details().taxId(), client.status());
  }
}
