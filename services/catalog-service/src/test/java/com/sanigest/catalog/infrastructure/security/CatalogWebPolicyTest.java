package com.sanigest.catalog.infrastructure.security;

import static com.sanigest.catalog.application.exception.ClientAccessException.Reason.CLIENT_UPDATE_CONFLICT;
import static com.sanigest.catalog.application.exception.ClientCreationException.Reason.AUTHORIZATION_UNAVAILABLE;
import static com.sanigest.catalog.application.port.out.integration.CatalogAuthorization.Permission.CATALOG_READ;
import static com.sanigest.catalog.application.port.out.integration.CatalogAuthorization.Permission.CATALOG_WRITE;
import static com.sanigest.catalog.domain.enums.SupplierStatus.ACTIVE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sanigest.catalog.application.dto.SupplierPage;
import com.sanigest.catalog.application.exception.CatalogAuthorizationUnavailable;
import com.sanigest.catalog.application.exception.ClientAccessException;
import com.sanigest.catalog.application.exception.ClientCreationException;
import com.sanigest.catalog.application.exception.SupplierAccessException;
import com.sanigest.catalog.application.exception.SupplierCreationException;
import com.sanigest.catalog.application.port.out.integration.CatalogAuthorization;
import com.sanigest.catalog.application.port.out.persistence.CatalogItemRepository;
import com.sanigest.catalog.application.port.out.persistence.ClientRepository;
import com.sanigest.catalog.application.port.out.persistence.SupplierRepository;
import com.sanigest.catalog.application.usecase.catalogitem.CreateCatalogItem;
import com.sanigest.catalog.application.usecase.client.ChangeClientStatus;
import com.sanigest.catalog.application.usecase.client.ClientCompany;
import com.sanigest.catalog.application.usecase.client.CreateClient;
import com.sanigest.catalog.application.usecase.client.GetClient;
import com.sanigest.catalog.application.usecase.client.ListClients;
import com.sanigest.catalog.application.usecase.client.UpdateClient;
import com.sanigest.catalog.application.usecase.supplier.ChangeSupplierStatus;
import com.sanigest.catalog.application.usecase.supplier.CreateSupplier;
import com.sanigest.catalog.application.usecase.supplier.GetSupplier;
import com.sanigest.catalog.application.usecase.supplier.ListSuppliers;
import com.sanigest.catalog.application.usecase.supplier.SupplierCompany;
import com.sanigest.catalog.application.usecase.supplier.UpdateSupplier;
import com.sanigest.catalog.domain.model.Client;
import com.sanigest.catalog.domain.model.ClientDetails;
import com.sanigest.catalog.domain.model.Supplier;
import com.sanigest.catalog.domain.model.SupplierDetails;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest
@Import({
  SecurityConfiguration.class,
  CreateCatalogItem.class,
  CreateClient.class,
  ListClients.class,
  ClientCompany.class,
  GetClient.class,
  UpdateClient.class,
  ChangeClientStatus.class,
  CreateSupplier.class,
  GetSupplier.class,
  ListSuppliers.class,
  SupplierCompany.class,
  UpdateSupplier.class,
  ChangeSupplierStatus.class
})
class CatalogWebPolicyTest {
  @Autowired MockMvc mvc;
  @MockitoBean JwtDecoder decoder;
  @MockitoBean ClientRepository clients;
  @MockitoBean CatalogItemRepository items;
  @MockitoBean SupplierRepository suppliers;
  @MockitoBean CatalogAuthorization authorization;

  @Test
  void publishesTheOpenApiContractAsJson() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.openapi").value("3.0.3"))
        .andExpect(jsonPath("$.paths['/actuator/health/readiness'].get.responses['503']").exists());
  }

  @Test
  void rejectsAnonymousRequestsOutsideTheExactGetAllowlist() throws Exception {
    mvc.perform(get("/api/v1/clients")).andExpect(status().isUnauthorized());
    mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    mvc.perform(get("/actuator/health/db")).andExpect(status().isUnauthorized());
    mvc.perform(post("/v3/api-docs")).andExpect(status().isUnauthorized());
  }

  @Test
  void listFailsClosedWithoutCompanyAuthorizationDespiteClientCompanyId() throws Exception {
    given(authorization.membership(CATALOG_READ))
        .willThrow(new ClientCreationException(AUTHORIZATION_UNAVAILABLE));
    validToken();

    mvc.perform(
            get("/api/v1/clients")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid")
                .param("companyId", "61fe639e-5460-4ca9-8e79-c3bd7ec6190d"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.errorCode").value("AUTHORIZATION_UNAVAILABLE"));
    verifyNoInteractions(clients);
  }

  @Test
  void rejectsInvalidListParameters() throws Exception {
    validToken();
    for (var invalid :
        new String[][] {
          {"page", "-1"},
          {"page", "abc"},
          {"size", "0"},
          {"size", "-1"},
          {"size", "101"},
          {"status", "DELETED"},
          {"search", "\0"}
        }) {
      mvc.perform(
              get("/api/v1/clients")
                  .header(HttpHeaders.AUTHORIZATION, "Bearer valid")
                  .param(invalid[0], invalid[1]))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }
    verifyNoInteractions(clients);
  }

  @Test
  void postRequiresAValidJwtAndFailsClosedWithoutCompanyAuthorization() throws Exception {
    given(authorization.membership(CATALOG_WRITE))
        .willThrow(new ClientCreationException(AUTHORIZATION_UNAVAILABLE));
    mvc.perform(post("/api/v1/clients")).andExpect(status().isUnauthorized());
    given(decoder.decode("invalid")).willThrow(new BadJwtException("Invalid token"));
    mvc.perform(post("/api/v1/clients").header(HttpHeaders.AUTHORIZATION, "Bearer invalid"))
        .andExpect(status().isUnauthorized());
    validToken();
    mvc.perform(
            post("/api/v1/clients")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid")
                .header("X-Company-Id", "61fe639e-5460-4ca9-8e79-c3bd7ec6190d")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Client\",\"companyId\":\"other\",\"userId\":\"other\"}"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.errorCode").value("AUTHORIZATION_UNAVAILABLE"));
    verifyNoInteractions(clients);
  }

  @Test
  void rejectsInvalidClientPayloads() throws Exception {
    validToken();
    for (String body :
        new String[] {
          "{}",
          "{\"name\":\"  \"}",
          "{\"name\":\"Client\",\"email\":\"invalid\"}",
          "{\"name\":\"Client\",\"phone\":\"invalid\"}",
          "{\"name\":\"" + "a".repeat(161) + "\"}",
          "{"
        }) {
      mvc.perform(
              post("/api/v1/clients")
                  .header(HttpHeaders.AUTHORIZATION, "Bearer valid")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }
    verifyNoInteractions(clients);
  }

  @Test
  void clientDetailAndPatchesRequireAuthenticationAndRejectMalformedInputs() throws Exception {
    var path = "/api/v1/clients/61fe639e-5460-4ca9-8e79-c3bd7ec6190d";
    mvc.perform(get(path)).andExpect(status().isUnauthorized());
    mvc.perform(patch(path)).andExpect(status().isUnauthorized());
    mvc.perform(patch(path + "/status")).andExpect(status().isUnauthorized());
    given(decoder.decode("invalid")).willThrow(new BadJwtException("Invalid token"));
    mvc.perform(patch(path).header(HttpHeaders.AUTHORIZATION, "Bearer invalid"))
        .andExpect(status().isUnauthorized());
    validToken();
    for (String badId : new String[] {"garbage", "1-1-1-1-1"}) {
      mvc.perform(get("/api/v1/clients/" + badId).header(HttpHeaders.AUTHORIZATION, "Bearer valid"))
          .andExpect(status().isBadRequest());
    }
    for (String body :
        new String[] {
          "{}",
          "[]",
          "{\"id\":null}",
          "{\"companyId\":null}",
          "{\"status\":\"ACTIVE\"}",
          "{\"email\":7}"
        }) {
      mvc.perform(
              patch(path)
                  .header(HttpHeaders.AUTHORIZATION, "Bearer valid")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }
    for (String body :
        new String[] {
          "{}",
          "{\"status\":\"DELETED\"}",
          "{\"status\":null}",
          "{\"status\":0}",
          "{\"status\":\"ACTIVE\",\"companyId\":null}"
        }) {
      mvc.perform(
              patch(path + "/status")
                  .header(HttpHeaders.AUTHORIZATION, "Bearer valid")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isBadRequest());
    }
    verifyNoInteractions(clients);
  }

  @Test
  void mapsScopedClientMutationsAndConflictsToHttp() throws Exception {
    validToken();
    var companyId = UUID.randomUUID();
    var client = Client.register(companyId, new ClientDetails("Client", "123", null, null, null));
    var path = "/api/v1/clients/" + client.id();
    given(authorization.membership(CATALOG_READ)).willReturn(Optional.of(companyId));
    given(clients.find(companyId, client.id())).willReturn(Optional.of(client), Optional.empty());
    mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer valid"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.taxId").value("123"));
    mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer valid"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("CLIENT_NOT_FOUND"));

    var email = "{\"email\":\"new@example.org\"}";
    writeRequest(path, email).andExpect(status().isForbidden());
    given(authorization.membership(CATALOG_WRITE)).willReturn(Optional.of(companyId));
    given(clients.update(eq(companyId), eq(client.id()), any()))
        .willAnswer(call -> call.<UnaryOperator<Client>>getArgument(2).apply(client))
        .willAnswer(call -> call.<UnaryOperator<Client>>getArgument(2).apply(client))
        .willThrow(new ClientAccessException(CLIENT_UPDATE_CONFLICT));
    writeRequest(path, email)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("new@example.org"));
    writeRequest(path + "/status", "{\"status\":\"INACTIVE\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("INACTIVE"));
    writeRequest(path, email)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("CLIENT_UPDATE_CONFLICT"));
  }

  @Test
  void mapsSupplierCreationAndDuplicateThroughTheHttpContract() throws Exception {
    validToken();
    var companyId = UUID.randomUUID();
    given(authorization.membership(CATALOG_WRITE)).willReturn(Optional.of(companyId));
    var body =
        """
        {"name":"Office","contact":"Laura","taxId":"  123 "}
        """;

    registerSupplier(body)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isString())
        .andExpect(jsonPath("$.companyId").value(companyId.toString()))
        .andExpect(jsonPath("$.taxId").value("123"))
        .andExpect(jsonPath("$.status").value("ACTIVE"));

    doThrow(new SupplierCreationException(SupplierCreationException.Reason.DUPLICATE_TAX_ID))
        .when(suppliers)
        .insert(any());
    registerSupplier(body)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("DUPLICATE_TAX_ID"));
  }

  @Test
  void rejectsSupplierWritesWhenCompanyDeniesOrCannotAuthorize() throws Exception {
    validToken();
    var body =
        """
        {"name":"Office","contact":"Laura"}
        """;
    given(authorization.membership(CATALOG_WRITE))
        .willReturn(Optional.empty())
        .willThrow(new CatalogAuthorizationUnavailable());

    registerSupplier("{\"name\":\"Office\\u0000\",\"contact\":\"Laura\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    registerSupplier(body)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    registerSupplier(body)
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.errorCode").value("AUTHORIZATION_UNAVAILABLE"));
    verifyNoInteractions(suppliers);
  }

  @Test
  void validatesSupplierQueryAndPatchShapesBeforeAccessingPersistence() throws Exception {
    validToken();
    var path = "/api/v1/suppliers/" + UUID.randomUUID();
    for (var invalid :
        new String[][] {
          {"page", "-1"}, {"page", "abc"}, {"size", "0"}, {"size", "101"}, {"status", "DELETED"}
        }) {
      mvc.perform(
              get("/api/v1/suppliers")
                  .header(HttpHeaders.AUTHORIZATION, "Bearer valid")
                  .param(invalid[0], invalid[1]))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }
    for (var invalid : new String[] {"not-a-uuid", "1-1-1-1-1"}) {
      mvc.perform(
              get("/api/v1/suppliers/" + invalid).header(HttpHeaders.AUTHORIZATION, "Bearer valid"))
          .andExpect(status().isBadRequest());
    }
    for (var invalid :
        new String[] {
          "{}",
          "[]",
          "null",
          "{\"revision\":1}",
          "{\"companyId\":null}",
          "{\"id\":null}",
          "{\"status\":\"ACTIVE\"}",
          "{\"taxId\":1}"
        }) {
      writeRequest(path, invalid).andExpect(status().isBadRequest());
    }
    for (var invalid :
        new String[] {
          "{}",
          "{\"status\":null}",
          "{\"status\":\"UNKNOWN\"}",
          "{\"status\":\"ACTIVE\",\"name\":\"Other\"}"
        }) {
      writeRequest(path + "/status", invalid).andExpect(status().isBadRequest());
    }
    verifyNoInteractions(suppliers);
  }

  @Test
  void mapsSupplierPagesAndAccessErrorsToTheHttpContract() throws Exception {
    validToken();
    var companyId = UUID.randomUUID();
    var supplier =
        Supplier.register(companyId, new SupplierDetails("Office", "123", "Laura", null, null));
    given(authorization.membership(CATALOG_READ)).willReturn(Optional.of(companyId));
    given(authorization.membership(CATALOG_WRITE)).willReturn(Optional.of(companyId));
    given(suppliers.search(companyId, null, null, 0, 20))
        .willReturn(
            new SupplierPage(
                List.of(new SupplierPage.Item(supplier.id(), "Office", "123", ACTIVE)), 0, 20, 1));
    var path = "/api/v1/suppliers/" + supplier.id();
    given(suppliers.find(companyId, supplier.id()))
        .willReturn(Optional.of(supplier), Optional.empty());
    given(suppliers.update(eq(companyId), eq(supplier.id()), any()))
        .willAnswer(call -> call.<UnaryOperator<Supplier>>getArgument(2).apply(supplier))
        .willAnswer(call -> call.<UnaryOperator<Supplier>>getArgument(2).apply(supplier))
        .willThrow(new SupplierAccessException(SupplierAccessException.Reason.DUPLICATE_TAX_ID))
        .willThrow(
            new SupplierAccessException(SupplierAccessException.Reason.SUPPLIER_UPDATE_CONFLICT));
    mvc.perform(get("/api/v1/suppliers").header(HttpHeaders.AUTHORIZATION, "Bearer valid"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(supplier.id().toString()))
        .andExpect(jsonPath("$.totalPages").value(1));
    mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer valid"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.contact").value("Laura"));
    mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer valid"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("SUPPLIER_NOT_FOUND"));
    writeRequest(path, "{\"taxId\":null,\"email\":\"new@example.org\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.taxId").isEmpty())
        .andExpect(jsonPath("$.email").value("new@example.org"));
    writeRequest(path + "/status", "{\"status\":\"INACTIVE\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("INACTIVE"));
    writeRequest(path, "{\"taxId\":\"duplicate\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("DUPLICATE_TAX_ID"));
    writeRequest(path + "/status", "{\"status\":\"INACTIVE\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("SUPPLIER_UPDATE_CONFLICT"));
  }

  private ResultActions registerSupplier(String body) throws Exception {
    return mvc.perform(
        post("/api/v1/suppliers")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private ResultActions writeRequest(String path, String body) throws Exception {
    return mvc.perform(
        patch(path)
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private void validToken() {
    given(decoder.decode("valid"))
        .willReturn(
            Jwt.withTokenValue("valid")
                .header("alg", "RS256")
                .subject("2526a433-d969-4663-9f3a-18693e0757aa")
                .expiresAt(Instant.now().plusSeconds(120))
                .build());
  }

  @Test
  void rejectsAnInvalidBearerToken() throws Exception {
    given(decoder.decode("invalid")).willThrow(new BadJwtException("Invalid token"));
    mvc.perform(get("/api/v1/clients").header(HttpHeaders.AUTHORIZATION, "Bearer invalid"))
        .andExpect(status().isUnauthorized());
  }
}
