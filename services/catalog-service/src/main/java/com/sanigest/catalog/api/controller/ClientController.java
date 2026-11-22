package com.sanigest.catalog.api.controller;

import com.sanigest.catalog.api.dto.ClientDetailResponse;
import com.sanigest.catalog.api.dto.ClientPageResponse;
import com.sanigest.catalog.api.dto.ClientPatchRequest;
import com.sanigest.catalog.api.dto.ClientResponse;
import com.sanigest.catalog.api.dto.CreateClientRequest;
import com.sanigest.catalog.application.ChangeClientStatus;
import com.sanigest.catalog.application.CreateClient;
import com.sanigest.catalog.application.GetClient;
import com.sanigest.catalog.application.ListClients;
import com.sanigest.catalog.application.UpdateClient;
import com.sanigest.catalog.domain.InvalidClientPatch;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
class ClientController {
  private final CreateClient createClient;

  private final ListClients listClients;

  private final GetClient getClient;
  private final UpdateClient updateClient;
  private final ChangeClientStatus changeClientStatus;

  ClientController(
      CreateClient createClient,
      ListClients listClients,
      GetClient getClient,
      UpdateClient updateClient,
      ChangeClientStatus changeClientStatus) {
    this.createClient = createClient;
    this.listClients = listClients;
    this.getClient = getClient;
    this.updateClient = updateClient;
    this.changeClientStatus = changeClientStatus;
  }

  @GetMapping("/api/v1/clients/{id}")
  ClientDetailResponse detail(@PathVariable String id) {
    return ClientDetailResponse.from(getClient.execute(clientId(id)));
  }

  @PatchMapping("/api/v1/clients/{id}")
  ClientDetailResponse update(@PathVariable String id, @RequestBody JsonNode body) {
    return ClientDetailResponse.from(
        updateClient.execute(clientId(id), ClientPatchRequest.details(body)));
  }

  @PatchMapping("/api/v1/clients/{id}/status")
  ClientDetailResponse status(@PathVariable String id, @RequestBody JsonNode body) {
    return ClientDetailResponse.from(
        changeClientStatus.execute(clientId(id), ClientPatchRequest.status(body)));
  }

  private static UUID clientId(String id) {
    try {
      var uuid = UUID.fromString(id);
      if (uuid.toString().equalsIgnoreCase(id)) {
        return uuid;
      }
    } catch (IllegalArgumentException invalid) {
      throw new InvalidClientPatch();
    }
    throw new InvalidClientPatch();
  }

  @GetMapping("/api/v1/clients")
  ClientPageResponse list(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = ListClients.DEFAULT_SIZE) int size) {
    return ClientPageResponse.from(listClients.execute(search, status, page, size));
  }

  @PostMapping("/api/v1/clients")
  @ResponseStatus(HttpStatus.CREATED)
  ClientResponse create(@Valid @RequestBody CreateClientRequest request) {
    return ClientResponse.from(createClient.execute(request.toDetails()));
  }
}
