package com.sanigest.catalog.api.controller;

import com.sanigest.catalog.api.dto.ClientResponse;
import com.sanigest.catalog.api.dto.CreateClientRequest;
import com.sanigest.catalog.application.CreateClient;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ClientController {
  private final CreateClient createClient;

  ClientController(CreateClient createClient) {
    this.createClient = createClient;
  }

  @PostMapping("/api/v1/clients")
  @ResponseStatus(HttpStatus.CREATED)
  ClientResponse create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateClientRequest request) {
    return ClientResponse.from(
        createClient.execute(
            UUID.fromString(jwt.getSubject()), jwt.getTokenValue(), request.toDetails()));
  }
}
