package com.sanigest.catalog.api.controller;

import com.sanigest.catalog.api.dto.ClientPageResponse;
import com.sanigest.catalog.api.dto.ClientResponse;
import com.sanigest.catalog.api.dto.CreateClientRequest;
import com.sanigest.catalog.application.CreateClient;
import com.sanigest.catalog.application.ListClients;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ClientController {
  private final CreateClient createClient;

  private final ListClients listClients;

  ClientController(CreateClient createClient, ListClients listClients) {
    this.createClient = createClient;
    this.listClients = listClients;
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
