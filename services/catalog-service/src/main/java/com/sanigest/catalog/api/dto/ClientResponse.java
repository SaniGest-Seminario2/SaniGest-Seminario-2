package com.sanigest.catalog.api.dto;

import com.sanigest.catalog.domain.Client;
import java.util.UUID;

public record ClientResponse(UUID id, String name, String taxId, Client.Status status) {
  public static ClientResponse from(Client client) {
    return new ClientResponse(
        client.id(), client.details().name(), client.details().taxId(), client.status());
  }
}
