package com.sanigest.catalog.api.controller;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.sanigest.catalog.api.dto.ClientDetailResponse;
import com.sanigest.catalog.api.dto.ClientListFilter;
import com.sanigest.catalog.api.dto.ClientPageResponse;
import com.sanigest.catalog.api.dto.ClientPatchRequest;
import com.sanigest.catalog.api.dto.CreateClientRequest;
import com.sanigest.catalog.application.dto.ClientPage;
import com.sanigest.catalog.application.port.out.integration.SalesClientFinancials.Statement;
import com.sanigest.catalog.application.usecase.client.ChangeClientStatus;
import com.sanigest.catalog.application.usecase.client.ClientFinancials;
import com.sanigest.catalog.application.usecase.client.CreateClient;
import com.sanigest.catalog.application.usecase.client.GetClient;
import com.sanigest.catalog.application.usecase.client.ListClients;
import com.sanigest.catalog.application.usecase.client.UpdateClient;
import com.sanigest.catalog.domain.exception.InvalidClientPatch;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
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
  private final ClientFinancials financials;
  private final Validator validator;

  ClientController(
      CreateClient createClient,
      ListClients listClients,
      GetClient getClient,
      UpdateClient updateClient,
      ChangeClientStatus changeClientStatus,
      ClientFinancials financials,
      Validator validator) {
    this.createClient = createClient;
    this.listClients = listClients;
    this.getClient = getClient;
    this.updateClient = updateClient;
    this.changeClientStatus = changeClientStatus;
    this.financials = financials;
    this.validator = validator;
  }

  @GetMapping("/api/v1/clients/{id}")
  ResponseEntity<ClientDetailResponse> detail(
      @PathVariable String id, @RequestParam(required = false) UUID expectedCompanyId) {
    var detail = ClientDetailResponse.from(getClient.execute(clientId(id), expectedCompanyId));
    var response = ResponseEntity.ok();
    if (expectedCompanyId != null) {
      response.header("X-Catalog-Company-Id", expectedCompanyId.toString());
    }
    return response.body(detail);
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
      @ModelAttribute ClientListFilter filter,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = ListClients.DEFAULT_SIZE) int size) {
    return ClientPageResponse.from(listClients.execute(filter.toCriteria(), page, size));
  }

  @GetMapping("/api/v1/clients/all")
  List<ClientPage.Item> all(@ModelAttribute ClientListFilter filter) {
    return listClients.all(filter.toCriteria());
  }

  @GetMapping("/api/v1/clients/summary")
  ClientFinancials.Summary summary() {
    return financials.summary();
  }

  @GetMapping("/api/v1/clients/{id}/financial-summary")
  FinancialProfile financialSummary(@PathVariable String id) {
    var profile = financials.profile(clientId(id));
    return new FinancialProfile(
        ClientDetailResponse.from(profile.client()),
        profile.balance(),
        profile.availableCredit(),
        profile.totalInvoiced(),
        profile.paymentsReceived(),
        profile.asOf());
  }

  @GetMapping("/api/v1/clients/{id}/statement")
  Statement statement(
      @PathVariable String id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = ListClients.DEFAULT_SIZE) int size) {
    return financials.statement(clientId(id), page, size);
  }

  record FinancialProfile(
      @JsonUnwrapped ClientDetailResponse client,
      BigDecimal balance,
      BigDecimal availableCredit,
      BigDecimal totalInvoiced,
      BigDecimal paymentsReceived,
      Instant asOf) {}

  @PostMapping("/api/v1/clients")
  @ResponseStatus(HttpStatus.CREATED)
  ClientDetailResponse create(@RequestBody JsonNode body) {
    var request = CreateClientRequest.from(body, validator);
    return ClientDetailResponse.from(createClient.execute(request.details()));
  }
}
