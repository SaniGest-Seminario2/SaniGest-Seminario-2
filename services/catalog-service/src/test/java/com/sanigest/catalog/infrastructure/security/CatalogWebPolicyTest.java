package com.sanigest.catalog.infrastructure.security;

import static com.sanigest.catalog.application.exception.client.ClientAccessException.Reason.CLIENT_UPDATE_CONFLICT;
import static com.sanigest.catalog.application.exception.client.ClientCreationException.Reason.AUTHORIZATION_UNAVAILABLE;
import static com.sanigest.catalog.application.port.out.integration.company.CatalogAuthorization.Permission.CATALOG_READ;
import static com.sanigest.catalog.application.port.out.integration.company.CatalogAuthorization.Permission.CATALOG_WRITE;
import static com.sanigest.catalog.domain.enums.supplier.SupplierStatus.ACTIVE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sanigest.catalog.application.dto.catalogitem.CatalogItemCommercialData;
import com.sanigest.catalog.application.dto.catalogitem.CatalogItemHistory;
import com.sanigest.catalog.application.dto.catalogitem.CatalogItemPage;
import com.sanigest.catalog.application.dto.catalogitem.CatalogItemReferences.Tax;
import com.sanigest.catalog.application.dto.catalogitem.CatalogItemReferences.Unit;
import com.sanigest.catalog.application.dto.catalogitem.CatalogItemSearchCriteria;
import com.sanigest.catalog.application.dto.catalogitem.CatalogItemSummary;
import com.sanigest.catalog.application.dto.client.ClientPage;
import com.sanigest.catalog.application.dto.client.ClientReferences.Option;
import com.sanigest.catalog.application.dto.supplier.SupplierPage;
import com.sanigest.catalog.application.dto.supplier.SupplierSearchCriteria;
import com.sanigest.catalog.application.exception.catalogitem.CatalogItemAccessException;
import com.sanigest.catalog.application.exception.client.ClientAccessException;
import com.sanigest.catalog.application.exception.client.ClientCreationException;
import com.sanigest.catalog.application.exception.client.ClientFinancialsUnavailable;
import com.sanigest.catalog.application.exception.security.CatalogAuthorizationUnavailable;
import com.sanigest.catalog.application.exception.supplier.SupplierAccessException;
import com.sanigest.catalog.application.exception.supplier.SupplierCreationException;
import com.sanigest.catalog.application.port.out.integration.company.CatalogAuthorization;
import com.sanigest.catalog.application.port.out.integration.expense.SupplierBalances;
import com.sanigest.catalog.application.port.out.integration.sales.SalesClientFinancials;
import com.sanigest.catalog.application.port.out.persistence.catalogitem.CatalogItemCommercialRepository;
import com.sanigest.catalog.application.port.out.persistence.catalogitem.CatalogItemRepository;
import com.sanigest.catalog.application.port.out.persistence.client.ClientCategoryRepository;
import com.sanigest.catalog.application.port.out.persistence.client.ClientReferenceRepository;
import com.sanigest.catalog.application.port.out.persistence.client.ClientRepository;
import com.sanigest.catalog.application.port.out.persistence.supplier.SupplierCategoryRepository;
import com.sanigest.catalog.application.port.out.persistence.supplier.SupplierItemRepository;
import com.sanigest.catalog.application.port.out.persistence.supplier.SupplierRepository;
import com.sanigest.catalog.application.usecase.catalogitem.CatalogBusinessDate;
import com.sanigest.catalog.application.usecase.catalogitem.CatalogItemCommercialQueries;
import com.sanigest.catalog.application.usecase.catalogitem.CatalogItemCompany;
import com.sanigest.catalog.application.usecase.catalogitem.CatalogItemWritePolicy;
import com.sanigest.catalog.application.usecase.catalogitem.ChangeCatalogItemStatus;
import com.sanigest.catalog.application.usecase.catalogitem.CreateCatalogItem;
import com.sanigest.catalog.application.usecase.catalogitem.GetCatalogItem;
import com.sanigest.catalog.application.usecase.catalogitem.ListCatalogItems;
import com.sanigest.catalog.application.usecase.catalogitem.UpdateCatalogItem;
import com.sanigest.catalog.application.usecase.client.ChangeClientStatus;
import com.sanigest.catalog.application.usecase.client.ClientCategories;
import com.sanigest.catalog.application.usecase.client.ClientCompany;
import com.sanigest.catalog.application.usecase.client.ClientProfileRules;
import com.sanigest.catalog.application.usecase.client.ClientReferenceData;
import com.sanigest.catalog.application.usecase.client.CreateClient;
import com.sanigest.catalog.application.usecase.client.GetClient;
import com.sanigest.catalog.application.usecase.client.ListClients;
import com.sanigest.catalog.application.usecase.client.UpdateClient;
import com.sanigest.catalog.application.usecase.supplier.ChangeSupplierStatus;
import com.sanigest.catalog.application.usecase.supplier.CreateSupplier;
import com.sanigest.catalog.application.usecase.supplier.GetSupplier;
import com.sanigest.catalog.application.usecase.supplier.ListSupplierLinks;
import com.sanigest.catalog.application.usecase.supplier.ListSuppliers;
import com.sanigest.catalog.application.usecase.supplier.ReplaceSupplierItems;
import com.sanigest.catalog.application.usecase.supplier.SupplierCategories;
import com.sanigest.catalog.application.usecase.supplier.SupplierCompany;
import com.sanigest.catalog.application.usecase.supplier.UpdateSupplier;
import com.sanigest.catalog.domain.enums.catalogitem.CatalogItemType;
import com.sanigest.catalog.domain.enums.client.ClientStatus;
import com.sanigest.catalog.domain.model.catalogitem.CatalogItem;
import com.sanigest.catalog.domain.model.catalogitem.CatalogItemCategory;
import com.sanigest.catalog.domain.model.catalogitem.CatalogItemProfile;
import com.sanigest.catalog.domain.model.client.Client;
import com.sanigest.catalog.domain.model.client.ClientCategory;
import com.sanigest.catalog.domain.model.client.ClientDetails;
import com.sanigest.catalog.domain.model.supplier.Supplier;
import com.sanigest.catalog.domain.model.supplier.SupplierDetails;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
  CatalogItemCommercialQueries.class,
  CatalogItemWritePolicy.class,
  CatalogBusinessDate.class,
  CatalogWebPolicyTest.ClockConfiguration.class,
  CatalogItemCompany.class,
  ChangeCatalogItemStatus.class,
  GetCatalogItem.class,
  UpdateCatalogItem.class,
  ListCatalogItems.class,
  com.sanigest.catalog.application.usecase.catalogitem.FilterCatalogStock.class,
  CreateCatalogItem.class,
  CreateClient.class,
  ListClients.class,
  com.sanigest.catalog.application.usecase.client.ClientFinancials.class,
  ClientCompany.class,
  ClientCategories.class,
  ClientReferenceData.class,
  ClientProfileRules.class,
  GetClient.class,
  UpdateClient.class,
  ChangeClientStatus.class,
  CreateSupplier.class,
  GetSupplier.class,
  ListSuppliers.class,
  SupplierCompany.class,
  UpdateSupplier.class,
  ChangeSupplierStatus.class,
  SupplierCategories.class,
  ReplaceSupplierItems.class,
  ListSupplierLinks.class
})
class CatalogWebPolicyTest {
  @org.springframework.boot.test.context.TestConfiguration
  static class ClockConfiguration {
    @org.springframework.context.annotation.Bean
    java.time.Clock testCatalogClock() {
      return java.time.Clock.systemUTC();
    }
  }

  @Autowired MockMvc mvc;
  @MockitoBean com.sanigest.web.application.dto.audit.AuditPage.Reader auditEntries;
  @MockitoBean CatalogItemCommercialRepository itemCommercial;
  @MockitoBean JwtDecoder decoder;
  @MockitoBean ClientRepository clients;
  @MockitoBean ClientCategoryRepository clientCategories;
  @MockitoBean ClientReferenceRepository clientReferences;
  @MockitoBean CatalogItemRepository items;
  @MockitoBean SupplierRepository suppliers;
  @MockitoBean SupplierCategoryRepository supplierCategories;
  @MockitoBean SupplierItemRepository supplierItems;

  @MockitoBean SupplierBalances supplierBalances;

  @MockitoBean CatalogAuthorization authorization;
  @MockitoBean SalesClientFinancials sales;

  @MockitoBean
  com.sanigest.catalog.application.port.out.integration.inventory.InventoryAvailability inventory;

  @Test
  void createsAndReadsExpandedItemsThroughTheirPublishedContract() throws Exception {
    var tenant = UUID.randomUUID();
    var category = UUID.randomUUID();
    var base = "/api/v1/catalog-items";
    var profile =
        new CatalogItemProfile(
            "MON-01", "00123", category, "UNIT", new BigDecimal("8.1234"), "ITBIS_18", true, false);
    var item =
        new CatalogItem(
            UUID.randomUUID(),
            tenant,
            "Monitor",
            CatalogItemType.PRODUCT,
            new BigDecimal("10.00"),
            true,
            null,
            profile);
    given(authorization.membership(CATALOG_READ)).willReturn(Optional.of(tenant));
    given(authorization.membership(CATALOG_WRITE)).willReturn(Optional.of(tenant));
    given(itemCommercial.lockCategory(tenant, category))
        .willReturn(new CatalogItemCategory(category, tenant, "TECHNOLOGY", "Tecnología", true));
    given(itemCommercial.activeUnit("UNIT")).willReturn(true);
    given(itemCommercial.currentTax(eq("ITBIS_18"), any())).willReturn(true);
    given(itemCommercial.details(eq(tenant), any(), any()))
        .willReturn(
            Map.of(
                item.id(),
                new CatalogItemCommercialData(
                    "Tecnología",
                    null,
                    new BigDecimal("0.180000"),
                    LocalDate.of(2026, 1, 1),
                    null)));
    given(items.find(tenant, item.id())).willReturn(Optional.of(item));
    given(items.search(eq(tenant), any(CatalogItemSearchCriteria.class), eq(0), eq(20)))
        .willReturn(new CatalogItemPage(List.of(item), 0, 20, 1));
    given(items.update(eq(tenant), eq(item.id()), any())).willReturn(item, item.withActive(false));
    validToken();

    var request =
        """
        {"name":"Monitor","type":"PRODUCT","price":"10.0000","description":"LCD",
         "sku":" mon-01 ","barcode":"00123","categoryId":"%s","unitCode":"UNIT",
         "estimatedCost":"8.1234","taxClassificationCode":"ITBIS_18",
         "availableForSale":true,"availableForPurchase":false}
        """
            .formatted(category);
    mvc.perform(
            post(base)
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.sku").value("MON-01"))
        .andExpect(jsonPath("$.barcode").value("00123"))
        .andExpect(jsonPath("$.price").value(10.0));
    mvc.perform(
            get(base + "/{id}", item.id())
                .param("expectedCompanyId", tenant.toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.finalPrice").value("11.80"))
        .andExpect(jsonPath("$.grossProfit").value("1.8766"));
    mvc.perform(
            get(base)
                .param("categoryId", category.toString())
                .param("availableForSale", "true")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].categoryName").value("Tecnología"));
    mvc.perform(
            patch(base + "/{id}", item.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"MON-01\"}"))
        .andExpect(status().isOk());
    mvc.perform(
            patch(base + "/{id}/status", item.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.eligibleForSale").value(false));
  }

  @Test
  void publishesCommercialReferencesAndAuditsThroughCatalogPermissions() throws Exception {
    var tenant = UUID.randomUUID();
    var base = "/api/v1/catalog-items";
    var id = UUID.randomUUID();
    var category = new CatalogItemCategory(id, tenant, "TECHNOLOGY", "Tecnología", true);
    var item = CatalogItem.register(tenant, "Monitor", CatalogItemType.PRODUCT, BigDecimal.TEN);
    given(authorization.membership(CATALOG_READ)).willReturn(Optional.of(tenant));
    given(authorization.membership(CATALOG_WRITE)).willReturn(Optional.of(tenant));
    given(itemCommercial.categories(tenant)).willReturn(List.of(category));
    given(itemCommercial.units()).willReturn(List.of(new Unit("UNIT", "Unidad", true, false)));
    given(itemCommercial.taxes(any()))
        .willReturn(
            List.of(
                new Tax(
                    "ITBIS_18",
                    "ITBIS 18%",
                    new BigDecimal("0.180000"),
                    LocalDate.of(2026, 1, 1),
                    null)));
    given(itemCommercial.summary(tenant)).willReturn(new CatalogItemSummary(1, 1, 0, 1, 0));
    given(items.find(tenant, item.id())).willReturn(Optional.of(item));
    given(itemCommercial.history(tenant, item.id(), 0, 20))
        .willReturn(
            new CatalogItemHistory(
                List.of(new CatalogItemHistory.Entry(1, id, "CREATE", Instant.EPOCH, null, "{}")),
                0,
                20,
                1));
    validToken();

    mvc.perform(get(base + "/categories").header(HttpHeaders.AUTHORIZATION, "Bearer valid"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(id.toString()));
    mvc.perform(get(base + "/units").header(HttpHeaders.AUTHORIZATION, "Bearer valid"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].divisible").value(false));
    mvc.perform(
            get(base + "/tax-classifications").header(HttpHeaders.AUTHORIZATION, "Bearer valid"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].rate").value("0.180000"));
    mvc.perform(get(base + "/summary").header(HttpHeaders.AUTHORIZATION, "Bearer valid"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.products").value(1));
    mvc.perform(
            get(base + "/{id}/history", item.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].operation").value("CREATE"))
        .andExpect(jsonPath("$.totalPages").value(1));
    mvc.perform(
            get(base + "/{id}/history", UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid"))
        .andExpect(status().isNotFound());
  }

  @Test
  void deniesWritesToReadOnlyCategoryEndpoints() throws Exception {
    validToken();
    for (String resource : List.of("clients", "suppliers", "catalog-items")) {
      String path = "/api/v1/" + resource + "/categories";
      mvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, "Bearer valid"))
          .andExpect(status().isForbidden());
      mvc.perform(
              put(path + "/{id}", UUID.randomUUID())
                  .header(HttpHeaders.AUTHORIZATION, "Bearer valid"))
          .andExpect(status().isForbidden());
    }
  }

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
  void listsScopedBalancesAndKeepsCommercialDataWhenSalesIsUnavailable() throws Exception {
    validToken();
    var company = UUID.randomUUID();
    var first = UUID.randomUUID();
    var second = UUID.randomUUID();
    var rows =
        List.of(
            new ClientPage.Item(first, "Ana", null, "ACTIVE", null, null, null, null, null, null),
            new ClientPage.Item(
                second, "Juan", null, "INACTIVE", null, null, null, null, null, null));
    given(authorization.membership(CATALOG_READ)).willReturn(Optional.of(company));
    given(clients.search(eq(company), any(), eq(0), eq(20)))
        .willReturn(new ClientPage(rows, 0, 20, 2));
    given(clients.search(eq(company), any(), eq(0), eq(Integer.MAX_VALUE)))
        .willReturn(new ClientPage(rows, 0, Integer.MAX_VALUE, 2));
    given(sales.balances(company, List.of(first, second)))
        .willReturn(Map.of(first, new BigDecimal("35.00")))
        .willReturn(Map.of(first, new BigDecimal("35.00")))
        .willThrow(new ClientFinancialsUnavailable());

    mvc.perform(get("/api/v1/clients").header(HttpHeaders.AUTHORIZATION, "Bearer valid"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalPages").value(1))
        .andExpect(jsonPath("$.content[0].balance").value(35))
        .andExpect(jsonPath("$.content[1].balance").value(0));
    mvc.perform(get("/api/v1/clients/all").header(HttpHeaders.AUTHORIZATION, "Bearer valid"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(first.toString()))
        .andExpect(jsonPath("$[0].balance").value(35));
    mvc.perform(get("/api/v1/clients").header(HttpHeaders.AUTHORIZATION, "Bearer valid"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].name").value("Ana"))
        .andExpect(jsonPath("$.content[0].balance").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  void servesScopedFinancialIndicatorsAndStatements() throws Exception {
    validToken();
    var company = UUID.randomUUID();
    var asOf = Instant.parse("2026-11-26T12:00:00Z");
    var client = Client.register(company, new ClientDetails("Cliente", "123", null, null, null));
    var path = "/api/v1/clients/" + client.id();
    given(authorization.membership(CATALOG_READ)).willReturn(Optional.of(company));
    given(clients.find(company, client.id())).willReturn(Optional.of(client));
    given(clients.counts(company)).willReturn(new ClientRepository.Counts(4, 3));
    given(sales.summary(company))
        .willReturn(new SalesClientFinancials.CompanyTotals(2, new BigDecimal("35.00"), asOf));
    given(sales.totals(company, client.id()))
        .willReturn(
            new SalesClientFinancials.Totals(
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.TEN, new BigDecimal("9"), asOf));
    given(sales.statement(company, client.id(), 1, 2))
        .willReturn(
            new SalesClientFinancials.Statement(List.of(), List.of(), 1, 2, false, false, asOf));

    mvc.perform(get("/api/v1/clients/summary").header(HttpHeaders.AUTHORIZATION, "Bearer valid"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalClients").value(4))
        .andExpect(jsonPath("$.activeClients").value(3))
        .andExpect(jsonPath("$.clientsWithPendingBalance").value(2))
        .andExpect(jsonPath("$.totalPending").value(35));
    mvc.perform(get(path + "/financial-summary").header(HttpHeaders.AUTHORIZATION, "Bearer valid"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Cliente"))
        .andExpect(jsonPath("$.balance").value(1))
        .andExpect(jsonPath("$.availableCredit").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.paymentsReceived").value(9));
    mvc.perform(
            get(path + "/statement")
                .param("page", "1")
                .param("size", "2")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.payments").isEmpty());
    mvc.perform(
            get(path + "/statement")
                .param("size", "101")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid"))
        .andExpect(status().isBadRequest());
    given(sales.summary(company)).willThrow(new ClientFinancialsUnavailable());
    mvc.perform(get("/api/v1/clients/summary").header(HttpHeaders.AUTHORIZATION, "Bearer valid"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.errorCode").value("CLIENT_FINANCIALS_UNAVAILABLE"));
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
          {"clientType", "PERSON"},
          {"paymentTerms", "INVALID"},
          {"categoryId", "invalid"},
          {"sort", "status,asc"},
          {"search", "\0"}
        }) {
      mvc.perform(
              get("/api/v1/clients")
                  .header(HttpHeaders.AUTHORIZATION, "Bearer valid")
                  .param(invalid[0], invalid[1]))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.type").value("urn:sanigest:problem:invalid_request"))
          .andExpect(jsonPath("$.title").value("Bad Request"))
          .andExpect(jsonPath("$.status").value(400))
          .andExpect(jsonPath("$.instance").value("/api/v1/clients"))
          .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
          .andExpect(jsonPath("$.detail").isNotEmpty())
          .andExpect(jsonPath("$.timestamp").isString())
          .andExpect(jsonPath("$.traceId").isString());
    }
    verifyNoInteractions(clients);
  }

  @Test
  void exposesCategoryAndReferenceOptionsWithTheExistingReadPermission() throws Exception {
    var tenant = UUID.randomUUID();
    var category =
        new ClientCategory(UUID.randomUUID(), tenant, "REGULAR", "Regular", ClientStatus.ACTIVE);
    given(authorization.membership(CATALOG_READ)).willReturn(Optional.of(tenant));
    given(clientCategories.list(tenant)).willReturn(List.of(category));
    given(clientReferences.countries())
        .willReturn(List.of(new Option("DO", "República Dominicana")));
    validToken();

    mvc.perform(get("/api/v1/clients/categories").header(HttpHeaders.AUTHORIZATION, "Bearer valid"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].code").value("REGULAR"))
        .andExpect(jsonPath("$[0].id").value(category.id().toString()));
    mvc.perform(
            get("/api/v1/clients/reference-data/countries")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].code").value("DO"));
    given(authorization.membership(CATALOG_READ)).willReturn(Optional.empty());
    mvc.perform(get("/api/v1/clients/categories").header(HttpHeaders.AUTHORIZATION, "Bearer valid"))
        .andExpect(status().isForbidden());
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
                .content("{\"name\":\"Client\",\"clientType\":\"LEGAL\"}"))
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
          "{\"name\":\"Client\"}",
          "{\"name\":\"Client\",\"clientType\":null}",
          "{\"name\":\"Client\",\"clientType\":\"LEGAL\",\"email\":\"invalid\"}",
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
        {"name":"Office","contact":"Laura","taxId":"  012345678 "}
        """;

    given(supplierItems.lockItems(eq(companyId), any(), any()))
        .willReturn(
            java.util.Map.of(
                SUPPLIER_ITEM,
                new SupplierItemRepository.LockedItem(CatalogItemType.PRODUCT, true)));
    registerSupplier(body)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isString())
        .andExpect(jsonPath("$.companyId").value(companyId.toString()))
        .andExpect(jsonPath("$.taxId").value("012345678"))
        .andExpect(jsonPath("$.paymentTerms").value("CASH"))
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
    given(suppliers.search(eq(companyId), any(SupplierSearchCriteria.class), eq(0), eq(20)))
        .willReturn(
            new SupplierPage(
                List.of(new SupplierPage.Item(supplier.id(), "Office", "123", ACTIVE)), 0, 20, 1));
    given(supplierBalances.read(companyId, List.of(supplier.id())))
        .willReturn(Map.of(supplier.id(), new BigDecimal("25.00")));
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
        .andExpect(jsonPath("$.content[0].outstandingAmount").value(25.0))
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

  private static final UUID SUPPLIER_ITEM = UUID.randomUUID();

  private ResultActions registerSupplier(String body) throws Exception {
    var request =
        (tools.jackson.databind.node.ObjectNode)
            tools.jackson.databind.json.JsonMapper.shared().readTree(body);
    if (!request.has("taxId")) {
      request.put("taxId", "012345678");
    }
    request
        .put("phone", "8095550101")
        .put("email", "supplier@example.do")
        .put("supplyType", "PRODUCT")
        .put("status", "ACTIVE")
        .put("categoryId", UUID.randomUUID().toString())
        .putNull("creditDays");
    request
        .putArray("items")
        .addObject()
        .put("catalogItemId", SUPPLIER_ITEM.toString())
        .put("offeredCost", new BigDecimal("1.00"))
        .put("supplierReference", "SUP-1");
    return mvc.perform(
        post("/api/v1/suppliers")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid")
            .contentType(MediaType.APPLICATION_JSON)
            .content(request.toString()));
  }

  private ResultActions writeRequest(String path, String body) throws Exception {
    return mvc.perform(
        patch(path)
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  @Test
  void catalogItemUpdateReportsAnOptimisticConflict() throws Exception {
    validToken();
    var company = UUID.randomUUID();
    var id = UUID.randomUUID();
    given(authorization.membership(CATALOG_WRITE)).willReturn(Optional.of(company));
    given(items.update(eq(company), eq(id), any()))
        .willThrow(
            new CatalogItemAccessException(
                CatalogItemAccessException.Reason.CATALOG_ITEM_UPDATE_CONFLICT));
    mvc.perform(
            patch("/api/v1/catalog-items/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"price\":2}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("CATALOG_ITEM_UPDATE_CONFLICT"));
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
