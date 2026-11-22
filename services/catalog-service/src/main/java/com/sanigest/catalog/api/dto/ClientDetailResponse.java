package com.sanigest.catalog.api.dto;

import com.sanigest.catalog.domain.Client;
import java.util.UUID;

public record ClientDetailResponse(
    UUID id,
    String name,
    String taxId,
    String email,
    String phone,
    String address,
    Client.Status status) {
  public static ClientDetailResponse from(Client client) {
    var details = client.details();
    return new ClientDetailResponse(
        client.id(),
        details.name(),
        details.taxId(),
        details.email(),
        details.phone(),
        details.address(),
        client.status());
  }
}
